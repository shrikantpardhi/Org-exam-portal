package com.company.exam.common.job;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Background-job telemetry ({@code specs/21-P12-Hardening-V1.5.md §111}).
 *
 * <p>Exposes one gauge per job type and status — queue depth, active work, and failures — so the
 * worker-backlog dashboard and the worker-outage alert have something to evaluate against. Counts are
 * low cardinality: the job types are a fixed set and the statuses are the frozen lifecycle.
 *
 * <p>A gauge is evaluated when Prometheus scrapes, so each value is one small aggregate query. That
 * is acceptable for the local stack and keeps the reporting truthful (no cached backlog).
 */
@Component
public class JobStateMetrics {

    public static final String JOB_COUNT = "background_job_count";

    /** The statuses that matter operationally; CANCELLED is not a frozen status. */
    private static final Map<String, BackgroundJobStatus> STATUSES = Map.of(
            "queued", BackgroundJobStatus.QUEUED,
            "running", BackgroundJobStatus.RUNNING,
            "failed", BackgroundJobStatus.FAILED,
            "completed", BackgroundJobStatus.COMPLETED);

    private final BackgroundJobRepository jobRepository;
    private final MeterRegistry registry;

    public JobStateMetrics(BackgroundJobRepository jobRepository, MeterRegistry registry) {
        this.jobRepository = jobRepository;
        this.registry = registry;
        register();
    }

    private void register() {
        for (String statusName : STATUSES.keySet()) {
            String status = statusName;
            Gauge.builder(JOB_COUNT, () -> total(status))
                    .tags("status", status)
                    .description("Background jobs grouped by lifecycle status")
                    .register(registry);
        }
        for (String jobType : JobTypes.all()) {
            for (String statusName : STATUSES.keySet()) {
                String type = jobType;
                String status = statusName;
                Gauge.builder(JOB_COUNT, () -> count(type, status))
                        .tags("jobType", type, "status", status)
                        .description("Background jobs grouped by type and lifecycle status")
                        .register(registry);
            }
        }
    }

    private long total(String statusName) {
        BackgroundJobStatus status = STATUSES.get(statusName);
        return status == null ? 0 : jobRepository.countByStatus(status);
    }

    private long count(String jobType, String statusName) {
        BackgroundJobStatus status = STATUSES.get(statusName);
        return status == null ? 0 : jobRepository.countByJobTypeAndStatus(jobType, status);
    }
}
