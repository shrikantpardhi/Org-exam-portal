package com.company.exam.communication.worker;

import com.company.exam.common.job.BackgroundJob;
import com.company.exam.common.job.BackgroundJobRepository;
import com.company.exam.common.job.BackgroundJobStatus;
import com.company.exam.common.job.JobStateService;
import com.company.exam.common.job.JobTypes;
import com.company.exam.communication.config.CommunicationProperties;
import com.company.exam.communication.observability.CommunicationMetrics;
import com.company.exam.communication.service.EmailDeliveryService;
import com.company.exam.communication.service.RetryPolicy;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

/**
 * Asynchronous email delivery with bounded retry and backoff
 * ({@code specs/19-P10-Communication-V1.5.md §32-§36, §126}).
 *
 * <p>An email is never sent inside a business transaction. A job is claimed, one delivery attempt is
 * made, and on a retryable failure the job returns to the queue until the attempt budget is
 * exhausted, after which it fails permanently and raises an alert-level metric. The backoff is
 * exponential with jitter and a cap, and the due check uses the capped delay so a retry is never
 * attempted earlier than its window.
 *
 * <p>The bean is always present; only the scheduler that drives it is gated by
 * {@code app.communication.worker.enabled}, so tests can invoke {@link #processQueued()} directly.
 */
@Component
public class EmailDeliveryWorker {

    private static final Logger log = LoggerFactory.getLogger(EmailDeliveryWorker.class);

    private final BackgroundJobRepository jobRepository;
    private final EmailDeliveryService emailDeliveryService;
    private final JobStateService jobStateService;
    private final RetryPolicy retryPolicy;
    private final CommunicationMetrics metrics;
    private final int batchSize;

    public EmailDeliveryWorker(BackgroundJobRepository jobRepository, EmailDeliveryService emailDeliveryService,
            JobStateService jobStateService, RetryPolicy retryPolicy, CommunicationMetrics metrics,
            CommunicationProperties properties) {
        this.jobRepository = jobRepository;
        this.emailDeliveryService = emailDeliveryService;
        this.jobStateService = jobStateService;
        this.retryPolicy = retryPolicy;
        this.metrics = metrics;
        this.batchSize = properties.getWorkerBatchSize();
    }

    public int processQueued() {
        List<BackgroundJob> queued = jobRepository.findByJobTypeAndStatusOrderByQueuedOnAsc(JobTypes.EMAIL_DELIVERY,
                BackgroundJobStatus.QUEUED, PageRequest.of(0, batchSize));
        int processed = 0;
        for (BackgroundJob job : queued) {
            if (!due(job, Instant.now())) {
                continue;
            }
            if (jobStateService.claim(job.getId())) {
                process(job);
                processed++;
            }
        }
        return processed;
    }

    /** Attempts one delivery and applies the retry policy. */
    public EmailDeliveryService.DeliveryResult process(BackgroundJob job) {
        EmailDeliveryService.DeliveryResult result = emailDeliveryService.attempt(job);
        switch (result.outcome()) {
            case SENT -> jobStateService.complete(job.getId(), result.detail());
            case FAILED -> {
                metrics.emailExhausted();
                log.warn("Email delivery permanently failed for job {}: {}", job.getId(), result.detail());
                jobStateService.fail(job.getId(), result.detail(), "Permanent delivery failure");
            }
            case RETRY -> {
                int attempts = job.getAttemptCount() == null ? 1 : job.getAttemptCount();
                if (attempts >= emailDeliveryService.maxAttempts()) {
                    metrics.emailExhausted();
                    log.warn("Email delivery exhausted its retry budget for job {} after {} attempts", job.getId(),
                            attempts);
                    jobStateService.fail(job.getId(), "RETRY_EXHAUSTED", "Maximum delivery attempts reached");
                } else {
                    metrics.emailRetried();
                    jobStateService.reschedule(job.getId());
                }
            }
        }
        return result;
    }

    /** Whether a queued job is due for its next attempt ({@code §35}). */
    public boolean due(BackgroundJob job, Instant now) {
        if (job.getLastAttemptOn() == null || job.getAttemptCount() == null || job.getAttemptCount() == 0) {
            return true;
        }
        return !now.isBefore(job.getLastAttemptOn().plus(retryPolicy.cappedDelayForAttempt(job.getAttemptCount())));
    }
}
