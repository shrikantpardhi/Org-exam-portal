package com.company.exam.communication.gateway;

import com.company.exam.communication.config.CommunicationProperties;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * The reference {@link EmailGateway}: an in-memory provider for local, test and CI runs
 * ({@code specs/19-P10-Communication-V1.5.md §86, §174}).
 *
 * <p>It is <strong>not</strong> a production email provider. It records the messages it accepted and
 * supports failure injection (transient outage, permanent rejection) so the retry and isolation
 * behaviour can be exercised. A production provider is a separate adapter implementing the same
 * contract.
 */
@Component
@ConditionalOnProperty(name = "app.communication.provider", havingValue = "FAKE", matchIfMissing = true)
public class FakeEmailGateway implements EmailGateway {

    private static final EmailProviderId PROVIDER = new EmailProviderId("FAKE");

    private final List<RenderedEmail> sent = new CopyOnWriteArrayList<>();
    private final Map<String, EmailSendResult> byReference = new ConcurrentHashMap<>();
    private final AtomicInteger transientFailuresRemaining = new AtomicInteger();
    private final AtomicInteger permanentFailuresRemaining = new AtomicInteger();

    @SuppressWarnings("unused")
    private final CommunicationProperties properties;

    public FakeEmailGateway(CommunicationProperties properties) {
        this.properties = properties;
    }

    @Override
    public EmailProviderId provider() {
        return PROVIDER;
    }

    @Override
    public EmailSendResult send(RenderedEmail email) {
        if (permanentFailuresRemaining.get() > 0 && permanentFailuresRemaining.getAndDecrement() > 0) {
            return new EmailSendResult(null, SendOutcome.FAILED, false);
        }
        if (transientFailuresRemaining.get() > 0 && transientFailuresRemaining.getAndDecrement() > 0) {
            return new EmailSendResult(null, SendOutcome.TIMEOUT, true);
        }
        String reference = "FAKE-" + UUID.randomUUID();
        sent.add(email);
        EmailSendResult result = new EmailSendResult(reference, SendOutcome.SENT, false);
        byReference.put(reference, result);
        return result;
    }

    @Override
    public java.util.Optional<EmailSendResult> query(String providerReference) {
        return java.util.Optional.ofNullable(byReference.get(providerReference));
    }

    // ---------------------------------------------------------------- failure injection (tests)

    /** Makes the next {@code times} sends fail transiently ({@code §34}). */
    public void failTransiently(int times) {
        transientFailuresRemaining.set(times);
    }

    /** Makes the next {@code times} sends fail permanently ({@code §34}). */
    public void failPermanently(int times) {
        permanentFailuresRemaining.set(times);
    }

    public void reset() {
        sent.clear();
        byReference.clear();
        transientFailuresRemaining.set(0);
        permanentFailuresRemaining.set(0);
    }

    public List<RenderedEmail> sentEmails() {
        return List.copyOf(sent);
    }

    public int sentCount() {
        return sent.size();
    }
}
