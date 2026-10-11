package com.company.exam.delivery.observability;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

/** Proves the runtime observability counters are registered ({@code specs/14-P5-Delivery-V1.5.md §120}). */
class DeliveryMetricsTest {

    @Test
    void criticalRuntimeCountersAreRegistered() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        DeliveryMetrics metrics = new DeliveryMetrics(registry);

        metrics.attemptStarted("EXAM");
        metrics.attemptStarted("EXAM");
        metrics.snapshotPrepared();
        metrics.attemptSubmitted();
        metrics.staleResponse();

        assertThat(registry.get(DeliveryMetrics.ATTEMPT_STARTED).tag("mode", "EXAM").counter().count())
                .isEqualTo(2.0);
        assertThat(registry.get(DeliveryMetrics.SNAPSHOT_PREPARED).counter().count()).isEqualTo(1.0);
        assertThat(registry.get(DeliveryMetrics.ATTEMPT_SUBMITTED).counter().count()).isEqualTo(1.0);
        assertThat(registry.get(DeliveryMetrics.STALE_RESPONSE).counter().count()).isEqualTo(1.0);
    }
}
