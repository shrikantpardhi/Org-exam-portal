package com.company.exam.commerce.observability;

import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import org.springframework.stereotype.Component;

/**
 * Commerce operational metrics ({@code specs/18-P9-Commerce-V1.5.md §111}).
 *
 * <p>Low-cardinality counters only; no user identifiers or payment references are used as tags.
 */
@Component
public class CommerceMetrics {

    public static final String PURCHASE_CREATE = "commerce.purchase.create.count";
    public static final String PURCHASE_CREATE_FAILURE = "commerce.purchase.create.failure.count";
    public static final String PAYMENT_INITIATION = "commerce.payment.initiation.count";
    public static final String PAYMENT_CALLBACK = "commerce.payment.callback.count";
    public static final String PAYMENT_CALLBACK_DUPLICATE = "commerce.payment.callback.duplicate.count";
    public static final String PAYMENT_SUCCESS = "commerce.payment.success.count";
    public static final String PAYMENT_FAILURE = "commerce.payment.failure.count";
    public static final String PAYMENT_REFUND = "commerce.payment.refund.count";
    public static final String PAYMENT_REFUND_FAILURE = "commerce.payment.refund.failure.count";
    public static final String ACCESS_GRANT = "commerce.access.grant.count";
    public static final String ACCESS_REVOKE = "commerce.access.revoke.count";
    public static final String COUPON_VALIDATION = "commerce.coupon.validation.count";
    public static final String COUPON_VALIDATION_INVALID = "commerce.coupon.validation.invalid.count";
    public static final String AUTHORIZATION_ALLOW = "commerce.authorization.allow.count";
    public static final String AUTHORIZATION_DENY = "commerce.authorization.deny.count";
    public static final String RECONCILIATION_MISMATCH = "commerce.reconciliation.mismatch.count";
    public static final String CALLBACK_LATENCY = "commerce.payment.callback.latency";

    private final MeterRegistry registry;

    public CommerceMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void purchaseCreated() {
        registry.counter(PURCHASE_CREATE).increment();
    }

    public void purchaseCreateFailed() {
        registry.counter(PURCHASE_CREATE_FAILURE).increment();
    }

    public void paymentInitiated() {
        registry.counter(PAYMENT_INITIATION).increment();
    }

    public void paymentCallback(Duration duration) {
        registry.counter(PAYMENT_CALLBACK).increment();
        registry.timer(CALLBACK_LATENCY).record(duration);
    }

    public void paymentCallbackDuplicate() {
        registry.counter(PAYMENT_CALLBACK_DUPLICATE).increment();
    }

    public void paymentSucceeded() {
        registry.counter(PAYMENT_SUCCESS).increment();
    }

    public void paymentFailed() {
        registry.counter(PAYMENT_FAILURE).increment();
    }

    public void refunded() {
        registry.counter(PAYMENT_REFUND).increment();
    }

    public void refundFailed() {
        registry.counter(PAYMENT_REFUND_FAILURE).increment();
    }

    public void accessGranted() {
        registry.counter(ACCESS_GRANT).increment();
    }

    public void accessRevoked() {
        registry.counter(ACCESS_REVOKE).increment();
    }

    public void couponValidated(boolean valid) {
        registry.counter(COUPON_VALIDATION).increment();
        if (!valid) {
            registry.counter(COUPON_VALIDATION_INVALID).increment();
        }
    }

    public void authorization(boolean allowed) {
        registry.counter(allowed ? AUTHORIZATION_ALLOW : AUTHORIZATION_DENY).increment();
    }

    public void reconciliationMismatch() {
        registry.counter(RECONCILIATION_MISMATCH).increment();
    }
}
