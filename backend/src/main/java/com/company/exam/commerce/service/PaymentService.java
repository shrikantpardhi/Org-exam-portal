package com.company.exam.commerce.service;

import com.company.exam.commerce.config.CommerceProperties;
import com.company.exam.commerce.dto.CommerceDtos;
import com.company.exam.commerce.entity.PaymentRecord;
import com.company.exam.commerce.entity.PaymentStatus;
import com.company.exam.commerce.entity.Purchase;
import com.company.exam.commerce.entity.SubscriptionPlan;
import com.company.exam.commerce.gateway.PaymentGateway;
import com.company.exam.commerce.gateway.PaymentGatewayRegistry;
import com.company.exam.commerce.observability.CommerceMetrics;
import com.company.exam.commerce.repository.PaymentRecordRepository;
import com.company.exam.commerce.repository.PurchaseRepository;
import com.company.exam.common.api.ApiError;
import com.company.exam.common.audit.SecurityEventLogger;
import com.company.exam.common.exception.BusinessException;
import com.company.exam.common.exception.InvalidStateException;
import com.company.exam.common.exception.NotFoundException;
import com.company.exam.common.exception.ValidationException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Payment initiation and callback processing
 * ({@code specs/18-P9-Commerce-V1.5.md §12, §13, §21-§25, §29.5, §35}).
 *
 * <p>The callback is hostile input: authenticity, reference, amount and state are all verified
 * before any entitlement changes ({@code §22}). Repeated success callbacks are idempotent and never
 * create a second grant ({@code §21, §70}).
 */
@Service
public class PaymentService {

    private final PurchaseRepository purchaseRepository;
    private final PaymentRecordRepository paymentRepository;
    private final CommerceCatalogService catalog;
    private final AccessGrantService accessGrantService;
    private final PaymentGatewayRegistry gatewayRegistry;
    private final CommerceProperties properties;
    private final CommerceMetrics metrics;
    private final SecurityEventLogger audit;
    private final org.springframework.context.ApplicationEventPublisher events;

    public PaymentService(PurchaseRepository purchaseRepository, PaymentRecordRepository paymentRepository,
            CommerceCatalogService catalog, AccessGrantService accessGrantService,
            PaymentGatewayRegistry gatewayRegistry, CommerceProperties properties, CommerceMetrics metrics,
            SecurityEventLogger audit, org.springframework.context.ApplicationEventPublisher events) {
        this.purchaseRepository = purchaseRepository;
        this.paymentRepository = paymentRepository;
        this.catalog = catalog;
        this.accessGrantService = accessGrantService;
        this.gatewayRegistry = gatewayRegistry;
        this.properties = properties;
        this.metrics = metrics;
        this.audit = audit;
        this.events = events;
    }

    /** Initiates a payment for the caller's own purchase ({@code §35}). Idempotent per purchase. */
    @Transactional
    public CommerceDtos.PaymentInitiationResponse initiate(UUID userId, UUID purchaseId) {
        Purchase purchase = purchaseRepository.findByIdAndUserId(purchaseId, userId)
                .orElseThrow(() -> NotFoundException.of("Purchase", purchaseId));
        if (purchase.getPaymentStatus() == PaymentStatus.SUCCESS || purchase.getPaymentStatus() == PaymentStatus.REFUNDED) {
            throw new InvalidStateException("This purchase cannot be paid again");
        }
        PaymentRecord payment = paymentRepository.findByPurchaseId(purchaseId).orElseGet(() -> {
            PaymentRecord created = new PaymentRecord();
            created.setPurchaseId(purchaseId);
            created.setPaymentStatus(PaymentStatus.PENDING);
            return created;
        });
        if (payment.getId() != null && payment.getPaymentReference() != null
                && payment.getPaymentStatus() == PaymentStatus.PENDING) {
            // Already initiated: return the existing reference rather than creating a second one.
            return new CommerceDtos.PaymentInitiationResponse(purchaseId, payment.getPaymentStatus().name(),
                    payment.getPaymentReference(), null, "REDIRECT");
        }
        PaymentGateway.PaymentInitiationResult result = gatewayRegistry.active().initiate(
                new PaymentGateway.PaymentInitiationCommand(purchaseId, purchase.getAmount(),
                        properties.getCurrency(), purchaseId.toString()));
        payment.setPaymentReference(result.paymentReference());
        payment.setPaymentMethod(result.paymentMethod());
        payment.setPaymentStatus(PaymentStatus.PENDING);
        paymentRepository.saveAndFlush(payment);
        metrics.paymentInitiated();
        audit.audit(userId, "INITIATE", "PaymentRecord", payment.getId(), "Initiated payment");
        return new CommerceDtos.PaymentInitiationResponse(purchaseId, PaymentStatus.PENDING.name(),
                result.paymentReference(), result.checkoutToken(), result.nextAction());
    }

    @Transactional(readOnly = true)
    public CommerceDtos.PaymentView get(UUID userId, UUID purchaseId) {
        purchaseRepository.findByIdAndUserId(purchaseId, userId)
                .orElseThrow(() -> NotFoundException.of("Purchase", purchaseId));
        PaymentRecord payment = paymentRepository.findByPurchaseId(purchaseId)
                .orElseThrow(() -> NotFoundException.of("Payment for purchase", purchaseId));
        return toView(payment);
    }

    /**
     * Processes a provider callback ({@code §23}). All validation and the state transition and the
     * grant update happen in one transaction; any failure rolls back and activates no grant.
     */
    @Transactional
    public CommerceDtos.PaymentCallbackResponse callback(CommerceDtos.PaymentCallbackRequest request) {
        long startedAt = System.nanoTime();
        PaymentGateway gateway = gatewayRegistry.active();
        // Authenticity + normalization happen at the adapter boundary; an unauthentic callback throws.
        PaymentGateway.PaymentCallbackResult verified = gateway.verifyAndNormalize(
                new PaymentGateway.PaymentCallbackCommand(request.paymentReference(), request.providerStatus(),
                        request.amount(), request.currency(), request.signature()));

        PaymentRecord payment = paymentRepository.findByPaymentReference(verified.paymentReference())
                .orElseThrow(() -> new ValidationException("Unknown payment reference",
                        List.of(ApiError.of("paymentReference", "REFERENCE_NOT_FOUND",
                                "No payment matches this reference"))));
        Purchase purchase = purchaseRepository.findByIdForUpdate(payment.getPurchaseId())
                .orElseThrow(() -> NotFoundException.of("Purchase", payment.getPurchaseId()));
        SubscriptionPlan plan = catalog.requirePlan(purchase.getSubscriptionPlanId());

        // Idempotency: an already-applied payment produces no second business event or grant.
        if (payment.getPaymentStatus() == PaymentStatus.SUCCESS
                || payment.getPaymentStatus() == PaymentStatus.REFUNDED) {
            metrics.paymentCallbackDuplicate();
            metrics.paymentCallback(Duration.ofNanos(System.nanoTime() - startedAt));
            boolean granted = accessGrantService.hasActiveGrant(purchase.getUserId(), plan.getAccessPackageId());
            return new CommerceDtos.PaymentCallbackResponse(verified.paymentReference(), purchase.getId(),
                    purchase.getPaymentStatus().name(), granted, true);
        }

        // Amount verification: the provider claiming paid is not enough ({@code §22.1, §71}).
        if (verified.amount() == null || verified.amount().compareTo(purchase.getAmount()) != 0) {
            metrics.paymentFailed();
            audit.audit(null, "PAYMENT_AMOUNT_MISMATCH", "Purchase", purchase.getId(),
                    "Callback amount does not match the purchase amount");
            throw new BusinessException("Payment amount does not match the purchase amount",
                    List.of(ApiError.of("amount", "AMOUNT_MISMATCH",
                            "The provider amount does not match the expected purchase amount")));
        }

        boolean accessGranted = false;
        switch (verified.outcome()) {
            case SUCCESS -> {
                payment.setPaymentStatus(PaymentStatus.SUCCESS);
                payment.setPaymentDate(Instant.now());
                purchase.setPaymentStatus(PaymentStatus.SUCCESS);
                com.company.exam.commerce.entity.AccessGrant grant = accessGrantService.applyPaidGrant(
                        purchase.getUserId(), plan.getAccessPackageId(), purchase.getPurchaseDate(),
                        plan.getDurationDays() == null ? 0 : plan.getDurationDays(), null);
                accessGranted = true;
                metrics.paymentSucceeded();
                // The producer owns business truth; Communication only consumes the event (§18).
                events.publishEvent(new com.company.exam.common.events.BusinessEvents.PaymentSucceededEvent(
                        purchase.getId(), purchase.getUserId(), purchase.getAmount(), properties.getCurrency()));
                events.publishEvent(new com.company.exam.common.events.BusinessEvents.AccessGrantedEvent(
                        purchase.getUserId(), plan.getAccessPackageId(), grant.getStartDate(), grant.getEndDate()));
            }
            case FAILED -> {
                payment.setPaymentStatus(PaymentStatus.FAILED);
                payment.setPaymentDate(Instant.now());
                purchase.setPaymentStatus(PaymentStatus.FAILED);
                metrics.paymentFailed();
                events.publishEvent(new com.company.exam.common.events.BusinessEvents.PaymentFailedEvent(
                        purchase.getId(), purchase.getUserId()));
            }
            case REFUNDED -> {
                payment.setPaymentStatus(PaymentStatus.REFUNDED);
                payment.setPaymentDate(Instant.now());
                purchase.setPaymentStatus(PaymentStatus.REFUNDED);
                accessGrantService.revokeForPackage(purchase.getUserId(), plan.getAccessPackageId(), Instant.now(),
                        null);
                metrics.refunded();
                events.publishEvent(new com.company.exam.common.events.BusinessEvents.PaymentRefundedEvent(
                        purchase.getId(), purchase.getUserId()));
            }
            case PENDING -> {
                // No authoritative change yet; acknowledge.
            }
        }
        paymentRepository.saveAndFlush(payment);
        purchaseRepository.saveAndFlush(purchase);
        audit.audit(null, "PAYMENT_" + verified.outcome().name(), "Purchase", purchase.getId(),
                "Applied payment callback");
        metrics.paymentCallback(Duration.ofNanos(System.nanoTime() - startedAt));
        return new CommerceDtos.PaymentCallbackResponse(verified.paymentReference(), purchase.getId(),
                purchase.getPaymentStatus().name(), accessGranted, false);
    }

    private CommerceDtos.PaymentView toView(PaymentRecord payment) {
        return new CommerceDtos.PaymentView(payment.getPurchaseId(),
                payment.getPaymentStatus() == null ? null : payment.getPaymentStatus().name(),
                payment.getPaymentReference(), payment.getPaymentMethod(), payment.getPaymentDate());
    }
}
