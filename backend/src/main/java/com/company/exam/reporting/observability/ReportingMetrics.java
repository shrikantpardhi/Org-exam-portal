package com.company.exam.reporting.observability;

import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import org.springframework.stereotype.Component;

/**
 * Report observability ({@code specs/22-P13-Reporting-V1.5.md §18 by category}).
 *
 * <p>Low-cardinality counters only: the report type is a bounded catalog value, never a user id or a
 * filter value.
 */
@Component
public class ReportingMetrics {

    public static final String GENERATED = "reporting.report.generated";
    public static final String FAILED = "reporting.report.failed";
    public static final String QUEUED = "reporting.report.queued";
    public static final String ROWS = "reporting.report.rows";
    public static final String GENERATION_LATENCY = "reporting.report.generation.latency";
    public static final String DOWNLOAD = "reporting.report.download";
    public static final String DOWNLOAD_DENIED = "reporting.report.download.denied";

    private final MeterRegistry registry;

    public ReportingMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void generated(String reportType, int rowCount, Duration latency) {
        registry.counter(GENERATED, "reportType", reportType).increment();
        registry.counter(ROWS, "reportType", reportType).increment(rowCount);
        registry.timer(GENERATION_LATENCY, "reportType", reportType).record(latency);
    }

    public void failed(String reportType) {
        registry.counter(FAILED, "reportType", reportType).increment();
    }

    public void queued(String reportType) {
        registry.counter(QUEUED, "reportType", reportType).increment();
    }

    public void downloaded(boolean allowed) {
        registry.counter(allowed ? DOWNLOAD : DOWNLOAD_DENIED).increment();
    }
}
