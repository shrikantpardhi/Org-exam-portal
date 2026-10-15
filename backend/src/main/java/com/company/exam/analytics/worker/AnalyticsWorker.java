package com.company.exam.analytics.worker;

import com.company.exam.analytics.observability.AnalyticsMetrics;
import com.company.exam.analytics.service.AnalyticsRefreshService;
import com.company.exam.common.job.BackgroundJob;
import com.company.exam.common.job.BackgroundJobRepository;
import com.company.exam.common.job.BackgroundJobStatus;
import com.company.exam.common.job.JobTypes;
import com.company.exam.infrastructure.worker.WorkerExecutionTemplate;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * The analytics refresh and full-rebuild worker ({@code specs/17-P8-Analytics-V1.5.md §64, §65, §94, §106}).
 *
 * <p>It consumes queued {@code ANALYTICS_REFRESH} and {@code ANALYTICS_REBUILD} jobs, claims each
 * with a race-safe conditional update, and runs the bounded aggregation. Disabled in tests via
 * {@code app.analytics.worker.enabled=false}; refresh and rebuild are then invoked explicitly and
 * deterministically.
 */
@Component
@ConditionalOnProperty(name = "app.analytics.worker.enabled", havingValue = "true")
public class AnalyticsWorker {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsWorker.class);

    private final BackgroundJobRepository jobRepository;
    private final AnalyticsRefreshService refreshService;
    private final WorkerExecutionTemplate workerTemplate;
    private final AnalyticsMetrics metrics;

    @Value("${app.analytics.worker.batch-size:25}")
    private int batchSize;

    public AnalyticsWorker(BackgroundJobRepository jobRepository, AnalyticsRefreshService refreshService,
            WorkerExecutionTemplate workerTemplate, AnalyticsMetrics metrics) {
        this.jobRepository = jobRepository;
        this.refreshService = refreshService;
        this.workerTemplate = workerTemplate;
        this.metrics = metrics;
    }

    @Scheduled(fixedDelayString = "${app.analytics.worker.interval:PT30S}",
            initialDelayString = "${app.analytics.worker.initial-delay:PT20S}")
    public void processQueuedAnalytics() {
        int refreshed = processRefreshQueue();
        int rebuilt = processRebuildQueue();
        if (refreshed > 0 || rebuilt > 0) {
            log.info("Analytics worker refreshed={} rebuilt={}", refreshed, rebuilt);
        }
    }

    private int processRefreshQueue() {
        List<BackgroundJob> jobs = jobRepository.findByJobTypeAndStatusOrderByQueuedOnAsc(
                JobTypes.ANALYTICS_REFRESH, BackgroundJobStatus.QUEUED, PageRequest.of(0, batchSize));
        int processed = 0;
        for (BackgroundJob job : jobs) {
            if (claim(job)) {
                processRefresh(job);
                processed++;
            }
        }
        return processed;
    }

    private void processRefresh(BackgroundJob job) {
        metrics.refreshRequested();
        if (job.getReferenceId() == null) {
            jobRepository.markFailed(job.getId(), BackgroundJobStatus.FAILED, Instant.now(), "INVALID_JOB",
                    "Analytics refresh job has no attempt reference");
            return;
        }
        try {
            workerTemplate.execute(JobTypes.ANALYTICS_REFRESH,
                    () -> refreshService.refreshForAttempt(job.getReferenceId()));
            jobRepository.markCompleted(job.getId(), BackgroundJobStatus.COMPLETED, Instant.now(),
                    job.getReferenceId().toString());
        } catch (RuntimeException failure) {
            metrics.refreshFailed();
            log.warn("Analytics refresh failed for job {}: {}", job.getId(), failure.getMessage());
            jobRepository.markFailed(job.getId(), BackgroundJobStatus.FAILED, Instant.now(), "REFRESH_FAILED",
                    failure.getMessage() == null ? "refresh failed" : failure.getMessage());
        }
    }

    private int processRebuildQueue() {
        List<BackgroundJob> jobs = jobRepository.findByJobTypeAndStatusOrderByQueuedOnAsc(
                JobTypes.ANALYTICS_REBUILD, BackgroundJobStatus.QUEUED, PageRequest.of(0, batchSize));
        int processed = 0;
        for (BackgroundJob job : jobs) {
            if (claim(job)) {
                try {
                    workerTemplate.execute(JobTypes.ANALYTICS_REBUILD, refreshService::rebuildAll);
                    jobRepository.markCompleted(job.getId(), BackgroundJobStatus.COMPLETED, Instant.now(), null);
                } catch (RuntimeException failure) {
                    metrics.refreshFailed();
                    log.warn("Analytics rebuild failed for job {}: {}", job.getId(), failure.getMessage());
                    jobRepository.markFailed(job.getId(), BackgroundJobStatus.FAILED, Instant.now(),
                            "REBUILD_FAILED", failure.getMessage() == null ? "rebuild failed" : failure.getMessage());
                }
                processed++;
            }
        }
        return processed;
    }

    private boolean claim(BackgroundJob job) {
        return jobRepository.claim(job.getId(), BackgroundJobStatus.QUEUED, BackgroundJobStatus.RUNNING,
                Instant.now()) == 1;
    }
}
