package com.company.exam.communication.observability;

import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import org.springframework.stereotype.Component;

/**
 * Communication operational metrics ({@code specs/19-P10-Communication-V1.5.md §135-§137}).
 *
 * <p>Low-cardinality counters only; no recipient address, provider reference, or user identifier is
 * used as a tag.
 */
@Component
public class CommunicationMetrics {

    public static final String NOTIFICATION_CREATED = "communication.notification.created";
    // Counter names deliberately carry no trailing ".count": Micrometer appends "_total" to counters,
    // so a ".count" suffix would double up in the exported Prometheus metric (and so on for the
    // email family, which the worker-backlog dashboard queries).
    public static final String EMAIL_SEND = "communication.email.send";
    public static final String EMAIL_SENT = "communication.email.sent";
    public static final String EMAIL_FAILURE = "communication.email.failure";
    public static final String EMAIL_RETRY = "communication.email.retry";
    public static final String EMAIL_EXHAUSTED = "communication.email.retry.exhausted";
    public static final String TEMPLATE_MISSING = "communication.template.missing";
    public static final String FANOUT = "communication.announcement.fanout";
    public static final String FANOUT_BACKLOG = "communication.notification.backlog";
    public static final String EMAIL_SEND_LATENCY = "communication.email.send.latency";

    private final MeterRegistry registry;

    public CommunicationMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void notificationsCreated(int count) {
        if (count > 0) {
            registry.counter(NOTIFICATION_CREATED).increment(count);
        }
    }

    public void emailAttempt() {
        registry.counter(EMAIL_SEND).increment();
    }

    public void emailSent(Duration latency) {
        registry.counter(EMAIL_SENT).increment();
        registry.timer(EMAIL_SEND_LATENCY).record(latency);
    }

    public void emailFailed() {
        registry.counter(EMAIL_FAILURE).increment();
    }

    public void emailRetried() {
        registry.counter(EMAIL_RETRY).increment();
    }

    public void emailExhausted() {
        registry.counter(EMAIL_EXHAUSTED).increment();
    }

    public void templateMissing() {
        registry.counter(TEMPLATE_MISSING).increment();
    }

    public void announcementFanout(int recipients) {
        registry.counter(FANOUT).increment();
        registry.counter(FANOUT_BACKLOG).increment(recipients);
    }
}
