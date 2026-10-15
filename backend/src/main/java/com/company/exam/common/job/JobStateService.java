package com.company.exam.common.job;

import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transactional {@code background_job} state transitions, shared by every worker
 * ({@code specs/04-Database-Schema-V1.5.md §35}).
 *
 * <p>The repository's claim/complete/fail/reschedule methods are {@code @Modifying} queries, so they
 * must run inside a transaction. A worker loop has no transaction of its own, which makes a direct
 * repository call fail with {@code TransactionRequiredException}; routing every transition through
 * this service keeps each transition its own short transaction, so a worker never holds a transaction
 * across external work and a bounded batch never becomes one long transaction.
 */
@Service
public class JobStateService {

    private final BackgroundJobRepository jobRepository;

    public JobStateService(BackgroundJobRepository jobRepository) {
        this.jobRepository = jobRepository;
    }

    /** Races to claim a queued job; only the winner may process it. */
    @Transactional
    public boolean claim(UUID jobId) {
        return jobRepository.claim(jobId, BackgroundJobStatus.QUEUED, BackgroundJobStatus.RUNNING, Instant.now()) == 1;
    }

    @Transactional
    public void complete(UUID jobId, String resultReference) {
        jobRepository.markCompleted(jobId, BackgroundJobStatus.COMPLETED, Instant.now(), resultReference);
    }

    @Transactional
    public void fail(UUID jobId, String errorCode, String errorMessage) {
        jobRepository.markFailed(jobId, BackgroundJobStatus.FAILED, Instant.now(), errorCode, errorMessage);
    }

    /** Returns a running job to the queue for a bounded retry, preserving its attempt history. */
    @Transactional
    public void reschedule(UUID jobId) {
        jobRepository.reschedule(jobId, BackgroundJobStatus.RUNNING, BackgroundJobStatus.QUEUED);
    }
}
