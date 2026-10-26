package com.company.exam.identity.service;

import com.company.exam.communication.service.EmailDeliveryService;
import com.company.exam.config.CorsProperties;
import com.company.exam.identity.entity.User;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Email-backed password-reset delivery (ADR-0034; closes GAP-002).
 *
 * <p>Resets (and therefore first passwords, which use the same reset-link flow) are delivered
 * through the Communication module's durable email pipeline: a {@code background_job} is queued and
 * the {@code EmailDeliveryWorker} renders the {@code PASSWORD_RESET} template and hands it to the
 * active {@code EmailGateway}. The token itself is never logged ({@code specs/10-P1 §27, §167}).
 *
 * <p>The reset link is built from the configured trusted frontend origin
 * ({@code specs/10-P1 §144}), never from a request Host header.
 *
 * <p>{@code @Primary} because the legacy {@link LoggingPasswordResetDelivery} remains as the
 * controlled test/no-op fallback documented in {@code specs/10-P1 §184}.
 */
@Primary
@Component
public class EmailPasswordResetDelivery implements PasswordResetDelivery {

    private static final Logger log = LoggerFactory.getLogger(EmailPasswordResetDelivery.class);

    private final EmailDeliveryService emailDeliveryService;
    private final CorsProperties corsProperties;

    public EmailPasswordResetDelivery(EmailDeliveryService emailDeliveryService, CorsProperties corsProperties) {
        this.emailDeliveryService = emailDeliveryService;
        this.corsProperties = corsProperties;
    }

    @Override
    public void deliver(User user, String resetToken) {
        String origin = trustedOrigin();
        if (origin == null) {
            log.warn("No trusted frontend origin configured; reset email not queued for userId={}", user.getId());
            return;
        }
        String resetLink = origin + "/reset-password?token=" + resetToken;
        String name = user.getFirstName() == null ? "" : user.getFirstName().strip();
        emailDeliveryService.enqueueEmail(user.getId(), "PASSWORD_RESET",
                Map.of("resetLink", resetLink, "studentName", name));
    }

    private String trustedOrigin() {
        if (corsProperties.getAllowedOrigins() == null || corsProperties.getAllowedOrigins().isEmpty()) {
            return null;
        }
        return corsProperties.getAllowedOrigins().get(0);
    }
}
