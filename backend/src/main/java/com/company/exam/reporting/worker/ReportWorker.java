package com.company.exam.reporting.worker;

import com.company.exam.common.job.BackgroundJob;
import com.company.exam.common.job.BackgroundJobRepository;
import com.company.exam.common.job.BackgroundJobStatus;
import com.company.exam.common.job.JobStateService;
import com.company.exam.common.job.JobTypes;
import com.company.exam.reporting.observability.ReportingMetrics;
import com.company.exam.reporting.service.ReportService;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

/**
 * Report generation worker ({@code specs/22-P13-Reporting-V1.5.md §93, §94}).
 *
 * <p>Large reports execute here, never on the request thread. The worker uses its <strong>own</strong>
 * bounded batch size and its own schedule so report work cannot starve exam-critical delivery or
 * evaluation workers (§94): the existing workers keep their own concurrency controls and this worker
 * claims at most a small number of report jobs per pass.
 *
 * <p>Job state is moved through {@link JobStateService} so every transition is its own short
 * transaction and a failure records a safe error code rather than a stack trace (§28).
 */
@Component
public class ReportWorker {

    private static final Logger log = LoggerFactory.getLogger(ReportWorker.class);

    private final BackgroundJobRepository jobRepository;
    private final JobStateService jobStateService;
    private final ReportService reportService;
    private final ReportingMetrics metrics;
    private final int batchSize;

    public ReportWorker(BackgroundJobRepository jobRepository, JobStateService jobStateService,
            ReportService reportService, ReportingMetrics metrics,
            @Value("${app.reporting.worker.batch-size:5}") int batchSize) {
        this.jobRepository = jobRepository;
        this.jobStateService = jobStateService;
        this.reportService = reportService;
        this.metrics = metrics;
        this.batchSize = Math.max(1, batchSize);
    }

    /** Processes one bounded batch of queued report jobs, oldest first. */
    public int processQueued() {
        List<BackgroundJob> queued = jobRepository.findByJobTypeAndStatusOrderByQueuedOnAsc(
                JobTypes.REPORT_GENERATION, BackgroundJobStatus.QUEUED, PageRequest.of(0, batchSize));
        int processed = 0;
        for (BackgroundJob job : queued) {
            if (jobStateService.claim(job.getId())) {
                process(job);
                processed++;
            }
        }
        return processed;
    }

    /** Generates one report and records its artifact reference as the job result (§30). */
    public void process(BackgroundJob job) {
        try {
            String storageKey = reportService.generate(job);
            jobStateService.complete(job.getId(), storageKey);
            log.info("Report job {} completed", job.getId());
        } catch (RuntimeException failure) {
            metrics.failed(job.getReferenceType());
            log.warn("Report job {} failed: {}", job.getId(), failure.getClass().getSimpleName());
            // Only a safe code and a fixed message are persisted; never the exception text or a stack trace.
            jobStateService.fail(job.getId(), "REPORT_GENERATION_FAILED",
                    "Report could not be generated for the requested filters");
        }
    }
}
