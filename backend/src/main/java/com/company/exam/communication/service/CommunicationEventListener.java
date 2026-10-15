package com.company.exam.communication.service;

import com.company.exam.common.events.BusinessEvents;
import com.company.exam.communication.observability.CommunicationMetrics;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Consumes business events and turns them into durable communication work
 * ({@code specs/19-P10-Communication-V1.5.md §6, §16, §19, §52-§55, §118}).
 *
 * <p>Handling happens <strong>after the business transaction commits</strong>, so a communication
 * failure can never roll back the business operation. The listener only enqueues a durable
 * {@code background_job}; all delivery happens in a worker ({@code §19, §32, §47}). Any failure here
 * is logged and swallowed — the business outcome is already committed ({@code §28}).
 */
@Component
public class CommunicationEventListener {

    private static final Logger log = LoggerFactory.getLogger(CommunicationEventListener.class);

    private final EmailDeliveryService emailDeliveryService;
    private final CommunicationMetrics metrics;

    public CommunicationEventListener(EmailDeliveryService emailDeliveryService, CommunicationMetrics metrics) {
        this.emailDeliveryService = emailDeliveryService;
        this.metrics = metrics;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBusinessEvent(BusinessEvents.BusinessEvent event) {
        try {
            handle(event);
        } catch (RuntimeException failure) {
            // The business transaction is already committed; communication must never undo it (§19, §28).
            log.error("Communication event handling failed for {}: {}", event.getClass().getSimpleName(),
                    failure.getMessage());
        }
    }

    private void handle(BusinessEvents.BusinessEvent event) {
        switch (event) {
            case BusinessEvents.PaymentSucceededEvent payment -> notify(payment.userId(), "Payment successful",
                    "Your payment was successful.", "PURCHASE_CONFIRMATION",
                    Map.of("purchaseSummary", payment.amount() == null ? "" : payment.amount().toPlainString()));
            case BusinessEvents.PaymentFailedEvent payment -> notify(payment.userId(), "Payment failed",
                    "Your payment could not be completed.", "PAYMENT_FAILED", Map.of());
            case BusinessEvents.PaymentRefundedEvent payment -> notify(payment.userId(), "Payment refunded",
                    "Your payment has been refunded and access has been revoked.", "PAYMENT_REFUNDED", Map.of());
            case BusinessEvents.AccessGrantedEvent grant -> notify(grant.userId(), "Access granted",
                    "Your access has been granted.", null, Map.of());
            case BusinessEvents.ResultPublishedEvent result -> notify(result.userId(), "Result published",
                    "Your result is now available.", "RESULT_PUBLISHED", Map.of());
            case BusinessEvents.AssessmentScheduledEvent ignored -> log.debug(
                    "Assessment scheduled event noted for communication");
            default -> log.debug("No communication mapping for {}", event.getClass().getSimpleName());
        }
    }

    private void notify(UUID userId, String title, String message, String templateCode,
            Map<String, String> variables) {
        if (userId == null) {
            return;
        }
        emailDeliveryService.enqueueNotificationRequest(new EmailDeliveryService.NotificationJobParameters(
                title.replace(' ', '_').toUpperCase(java.util.Locale.ROOT), List.of(userId), title, message,
                templateCode, variables));
        metrics.notificationsCreated(1);
    }
}
