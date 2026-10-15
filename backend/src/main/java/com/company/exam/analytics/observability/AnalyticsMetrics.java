package com.company.exam.analytics.observability;

import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import org.springframework.stereotype.Component;

/**
 * Analytics operational metrics ({@code specs/17-P8-Analytics-V1.5.md §119}).
 *
 * <p>Low-cardinality counters only; no student identifiers are used as tags.
 */
@Component
public class AnalyticsMetrics {

    public static final String REFRESH_TOTAL = "analytics_refresh_total";
    public static final String REFRESH_SUCCESS = "analytics_refresh_success_total";
    public static final String REFRESH_FAILURE = "analytics_refresh_failure_total";
    public static final String REFRESH_DURATION = "analytics_refresh_duration";
    public static final String REBUILD_TOTAL = "analytics_rebuild_total";
    public static final String ALERTS_TOTAL = "analytics_alerts_total";
    public static final String CACHE_HIT = "analytics_cache_hit";
    public static final String CACHE_MISS = "analytics_cache_miss";
    public static final String DATA_QUALITY_FAILURES = "analytics_data_quality_failures";

    private final MeterRegistry registry;

    public AnalyticsMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void refreshRequested() {
        registry.counter(REFRESH_TOTAL).increment();
    }

    public void refreshSucceeded(Duration duration) {
        registry.counter(REFRESH_SUCCESS).increment();
        registry.timer(REFRESH_DURATION).record(duration);
    }

    public void refreshFailed() {
        registry.counter(REFRESH_FAILURE).increment();
    }

    public void rebuildRan(Duration duration) {
        registry.counter(REBUILD_TOTAL).increment();
        registry.timer(REFRESH_DURATION, "mode", "rebuild").record(duration);
    }

    public void cacheHit() {
        registry.counter(CACHE_HIT).increment();
    }

    public void cacheMiss() {
        registry.counter(CACHE_MISS).increment();
    }

    public void dataQualityFailures(int failures) {
        if (failures > 0) {
            registry.counter(DATA_QUALITY_FAILURES).increment(failures);
        }
    }

    public void alert(String type) {
        registry.counter(ALERTS_TOTAL, "type", type).increment();
    }
}
