package com.company.exam.commerce.gateway;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * The payment provider boundary ({@code specs/18-P9-Commerce-V1.5.md §13}).
 *
 * <p>Provider-specific code, payloads, and secrets live below this interface. The rest of the
 * platform depends only on this contract, never on a concrete gateway SDK. Provider statuses are
 * normalized to {@link NormalizedOutcome} here ({@code §12}).
 */
public interface PaymentGateway {

    /** A stable provider identifier. */
    record PaymentProviderId(String value) {
    }

    /** The normalized provider outcome. Provider lifecycle values never reach the domain tables. */
    enum NormalizedOutcome {
        PENDING,
        SUCCESS,
        FAILED,
        REFUNDED
    }

    /** Provider state as reported by a query API, used by reconciliation ({@code §60}). */
    record ProviderPaymentState(String reference, NormalizedOutcome outcome, BigDecimal amount) {
    }

    record PaymentInitiationCommand(java.util.UUID purchaseId, BigDecimal amount, String currency,
            String idempotencyKey) {
    }

    /** The minimal initiation payload the client needs; provider secrets are never exposed ({@code §35}). */
    record PaymentInitiationResult(String paymentReference, String paymentMethod, String checkoutToken,
            String nextAction) {
    }

    /** The raw, hostile callback input before verification ({@code §22}). */
    record PaymentCallbackCommand(String paymentReference, String providerStatus, BigDecimal providerAmount,
            String currency, String signature) {
    }

    /** The verified and normalized callback result. */
    record PaymentCallbackResult(String paymentReference, NormalizedOutcome outcome, BigDecimal amount,
            String currency) {
    }

    record RefundCommand(java.util.UUID purchaseId, String paymentReference, BigDecimal amount,
            String idempotencyKey) {
    }

    record RefundResult(String paymentReference, boolean refunded) {
    }

    PaymentProviderId provider();

    PaymentInitiationResult initiate(PaymentInitiationCommand command);

    /** Verifies provider authenticity and normalizes the status; throws when authenticity fails. */
    PaymentCallbackResult verifyAndNormalize(PaymentCallbackCommand command);

    RefundResult refund(RefundCommand command);

    /** Optional provider query for reconciliation; empty when the provider cannot be queried. */
    default Optional<ProviderPaymentState> query(String paymentReference) {
        return Optional.empty();
    }
}
