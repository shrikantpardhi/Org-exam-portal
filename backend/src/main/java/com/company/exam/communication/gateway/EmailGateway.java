package com.company.exam.communication.gateway;

import java.util.Optional;

/**
 * The email provider boundary ({@code specs/19-P10-Communication-V1.5.md §29, §88, §190}).
 *
 * <p>Provider-specific code, payloads and secrets live below this interface. The Communication
 * domain depends only on this contract, never on a provider SDK.
 */
public interface EmailGateway {

    /** A stable provider identifier. */
    record EmailProviderId(String value) {
    }

    /** A rendered message ready to send. No JPA entity ever reaches the provider. */
    record RenderedEmail(String toAddress, String subject, String htmlBody) {
    }

    /** The normalized provider outcome. Provider lifecycle values never reach the domain. */
    enum SendOutcome {
        SENT,
        FAILED,
        TIMEOUT
    }

    /**
     * The normalized send result. {@code retryable} distinguishes transient failures (timeout, 5xx,
     * rate limit, provider unavailable) from permanent ones (invalid recipient, rejected content)
     * ({@code §34, §88}).
     */
    record EmailSendResult(String providerReference, SendOutcome outcome, boolean retryable) {

        public boolean sent() {
            return outcome == SendOutcome.SENT;
        }
    }

    EmailProviderId provider();

    EmailSendResult send(RenderedEmail email);

    /** Optional provider status query used when a send result is ambiguous ({@code §38}). */
    default Optional<EmailSendResult> query(String providerReference) {
        return Optional.empty();
    }
}
