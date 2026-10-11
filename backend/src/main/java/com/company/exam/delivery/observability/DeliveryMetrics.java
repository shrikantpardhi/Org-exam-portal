package com.company.exam.delivery.observability;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Runtime observability counters ({@code specs/14-P5-Delivery-V1.5.md §120, §148}).
 *
 * <p>Low-cardinality counters for the critical delivery paths. No question content, answers, or
 * identifiers are ever used as tags.
 */
@Component
public class DeliveryMetrics {

    public static final String ATTEMPT_STARTED = "delivery.attempt.started";
    public static final String SNAPSHOT_PREPARED = "delivery.snapshot.prepared";
    public static final String ATTEMPT_SUBMITTED = "delivery.attempt.submitted";
    public static final String STALE_RESPONSE = "delivery.response.stale";

    private final MeterRegistry registry;

    public DeliveryMetrics(MeterRegistry registry) {
        this.registry = registry;
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
}
