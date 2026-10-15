package com.company.exam.commerce.gateway;

import com.company.exam.common.api.ApiError;
import com.company.exam.common.exception.ApiException;
import com.company.exam.common.exception.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import com.company.exam.commerce.config.CommerceProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * The reference {@link PaymentGateway}: a deterministic, in-memory provider used for local, test and
 * CI runs ({@code specs/18-P9-Commerce-V1.5.md §137}).
 *
 * <p>It is <strong>not</strong> a production payment provider. It performs real callback
 * authenticity verification with an HMAC signature so that the security boundary is exercised
 * rather than bypassed ({@code §22, §70}). A production provider is a separate adapter implementing
 * the same contract.
 */
@Component
@ConditionalOnProperty(name = "app.commerce.gateway", havingValue = "TEST", matchIfMissing = true)
public class TestPaymentGateway implements PaymentGateway {

    private static final PaymentProviderId PROVIDER = new PaymentProviderId("TEST");

    private final CommerceProperties properties;
    private final Map<String, State> states = new ConcurrentHashMap<>();

    public TestPaymentGateway(CommerceProperties properties) {
        this.properties = properties;
    }

    @Override
    public PaymentProviderId provider() {
        return PROVIDER;
    }

    @Override
    public PaymentInitiationResult initiate(PaymentInitiationCommand command) {
        String reference = "TEST-" + UUID.randomUUID();
        states.put(reference, new State(reference, NormalizedOutcome.PENDING, command.amount()));
        return new PaymentInitiationResult(reference, "TEST_CARD", "TEST-TOKEN-" + reference, "REDIRECT");
    }

    @Override
    public PaymentCallbackResult verifyAndNormalize(PaymentCallbackCommand command) {
        String expected = signature(command.paymentReference(), command.providerStatus(), command.providerAmount(),
                command.currency());
        if (command.signature() == null || !constantTimeEquals(expected, command.signature())) {
            throw new ApiException(ErrorCode.AUTHORIZATION_ERROR, "Payment callback authenticity verification failed",
                    java.util.List.of(ApiError.of("signature", "CALLBACK_NOT_AUTHENTIC",
                            "The callback signature is not valid")));
        }
        NormalizedOutcome outcome = normalize(command.providerStatus());
        states.put(command.paymentReference(),
                new State(command.paymentReference(), outcome, command.providerAmount()));
        return new PaymentCallbackResult(command.paymentReference(), outcome, command.providerAmount(),
                command.currency());
    }

    @Override
    public RefundResult refund(RefundCommand command) {
        if (command.paymentReference() != null) {
            states.computeIfPresent(command.paymentReference(),
                    (key, state) -> new State(key, NormalizedOutcome.REFUNDED, state.amount()));
        }
        return new RefundResult(command.paymentReference(), true);
    }

    @Override
    public Optional<ProviderPaymentState> query(String paymentReference) {
        State state = states.get(paymentReference);
        return state == null ? Optional.empty()
                : Optional.of(new ProviderPaymentState(state.reference(), state.outcome(), state.amount()));
    }

    /** Builds the signature a legitimate provider callback must carry. Used by tests and fixtures. */
    public String signatureFor(String paymentReference, String providerStatus, BigDecimal amount, String currency) {
        return signature(paymentReference, providerStatus, amount, currency);
    }

    private NormalizedOutcome normalize(String providerStatus) {
        if (providerStatus == null) {
            return NormalizedOutcome.PENDING;
        }
        return switch (providerStatus.trim().toUpperCase(Locale.ROOT)) {
            case "CAPTURED", "SUCCESS", "PAID", "COMPLETED" -> NormalizedOutcome.SUCCESS;
            case "FAILED", "DECLINED", "ERROR" -> NormalizedOutcome.FAILED;
            case "REFUNDED" -> NormalizedOutcome.REFUNDED;
            default -> NormalizedOutcome.PENDING;
        };
    }

    private String signature(String reference, String status, BigDecimal amount, String currency) {
        String payload = String.join("|", nullSafe(reference), nullSafe(status),
                amount == null ? "" : amount.stripTrailingZeros().toPlainString(), nullSafe(currency));
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(properties.getGatewaySecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                hex.append(String.format("%02x", value));
            }
            return hex.toString();
        } catch (Exception failure) {
            throw new IllegalStateException("Unable to compute the callback signature", failure);
        }
    }

    private boolean constantTimeEquals(String expected, String provided) {
        byte[] a = expected.getBytes(StandardCharsets.UTF_8);
        byte[] b = provided.getBytes(StandardCharsets.UTF_8);
        if (a.length != b.length) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length; i++) {
            result |= a[i] ^ b[i];
        }
        return result == 0;
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }

    private record State(String reference, NormalizedOutcome outcome, BigDecimal amount) {
    }
}
