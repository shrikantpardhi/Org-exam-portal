package com.company.exam.delivery.observability;

import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/**
 * Runtime observability for the delivery paths ({@code specs/14-P5-Delivery-V1.5.md §120, §148}).
 *
 * <p>P12 §112 defines the minimum operational metrics for assessment delivery; this class is where
 * they are emitted. Latency is measured with Micrometer timers (exposed as
 * {@code *_seconds_count/sum/max}) and success with outcome counters tagged {@code result=success|
 * failure}, so a dashboard can show a rate and an error rate, and an alert can fire on a spike.
 *
 * <p>No question content, answers, identifiers, or user data is ever used as a tag or a value.
 */
@Component
public class DeliveryMetrics {

    public static final String ATTEMPT_STARTED = "delivery.attempt.started";
    public static final String SNAPSHOT_PREPARED = "delivery.snapshot.prepared";
    public static final String ATTEMPT_SUBMITTED = "delivery.attempt.submitted";
    public static final String STALE_RESPONSE = "delivery.response.stale";
    // P5 §90: the session expiry worker emits its own counter.
    public static final String SESSION_EXPIRED = "delivery.session.expired";

    // P12 §112: assessment start success and latency.
    public static final String ATTEMPT_START_DURATION = "delivery.attempt.start.duration";
    public static final String ATTEMPT_START = "delivery.attempt.start";
    // P12 §112: response save success and latency.
    public static final String RESPONSE_SAVE_DURATION = "delivery.response.save.duration";
    public static final String RESPONSE_SAVE = "delivery.response.save";
    // P12 §112: submission success and latency.
    public static final String SUBMISSION_DURATION = "delivery.submission.duration";
    public static final String SUBMISSION = "delivery.submission";
    // P12 §112: snapshot preparation duration (and readiness via the existing SNAPSHOT_PREPARED).
    public static final String SNAPSHOT_PREPARATION_DURATION = "delivery.snapshot.preparation.duration";
    // P12 §112: session recovery success.
    public static final String SESSION_RECOVERY = "delivery.session.recovery";
    // P12 §113: snapshot backlog (pending attempts awaiting preparation). A gauge, updated by the
    // preparation worker after each sweep, so the SnapshotBacklogHigh alert can fire.
    public static final String SNAPSHOT_BACKLOG = "delivery.snapshot.backlog";

    private final MeterRegistry registry;
    private final AtomicInteger snapshotBacklogGauge = new AtomicInteger(0);

    public DeliveryMetrics(MeterRegistry registry) {
        this.registry = registry;
        registry.gauge(SNAPSHOT_BACKLOG, snapshotBacklogGauge);
    }

    public void attemptStarted(String mode) {
        registry.counter(ATTEMPT_STARTED, "mode", mode == null ? "UNKNOWN" : mode).increment();
    }

    public void snapshotPrepared() {
        registry.counter(SNAPSHOT_PREPARED).increment();
    }

    public void attemptSubmitted() {
        registry.counter(ATTEMPT_SUBMITTED).increment();
    }

    public void staleResponse() {
        registry.counter(STALE_RESPONSE).increment();
    }

    /** A stale assessment session was expired by the session expiry worker (§90). */
    public void sessionExpired() {
        registry.counter(SESSION_EXPIRED).increment();
    }

    /** Times an operation and records its success/failure outcome (§112). */
    public <T> T attemptStart(Supplier<T> operation) {
        return timed(ATTEMPT_START_DURATION, ATTEMPT_START, operation);
    }

    public <T> T responseSave(Supplier<T> operation) {
        return timed(RESPONSE_SAVE_DURATION, RESPONSE_SAVE, operation);
    }

    public <T> T submission(Supplier<T> operation) {
        return timed(SUBMISSION_DURATION, SUBMISSION, operation);
    }

    /** Session recovery is recorded as an outcome; a duration is not required by §112. */
    public <T> T sessionRecovery(Supplier<T> operation) {
        return timed(null, SESSION_RECOVERY, operation);
    }

    /** Snapshot preparation duration, recorded by the preparation worker (§112). */
    public void snapshotPreparationDuration(Duration duration) {
        registry.timer(SNAPSHOT_PREPARATION_DURATION).record(duration);
    }

    /** Updates the snapshot backlog gauge after a preparation sweep (§113). */
    public void snapshotBacklog(int pendingCount) {
        snapshotBacklogGauge.set(Math.max(0, pendingCount));
    }

    private <T> T timed(String durationMetric, String outcomeMetric, Supplier<T> operation) {
        long start = System.nanoTime();
        try {
            T result = operation.get();
            registry.counter(outcomeMetric, "result", "success").increment();
            return result;
        } catch (RuntimeException failure) {
            registry.counter(outcomeMetric, "result", "failure").increment();
            throw failure;
        } finally {
            if (durationMetric != null) {
                registry.timer(durationMetric).record(Duration.ofNanos(System.nanoTime() - start));
            }
        }
    }
}
