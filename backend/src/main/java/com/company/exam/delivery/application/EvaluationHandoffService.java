package com.company.exam.delivery.application;

import com.company.exam.common.audit.CurrentAuditorProvider;
import com.company.exam.common.job.BackgroundJob;
import com.company.exam.common.job.BackgroundJobRepository;
import com.company.exam.common.job.BackgroundJobStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Durable, idempotent evaluation handoff ({@code specs/14-P5-Delivery-V1.5.md §67, §66}).
 *
 * <p>P5 stops at {@code StudentAttempt = SUBMITTED} and enqueues a {@code background_job} of type
 * {@code EVALUATION} using the system actor identity ({@code §14 invariant 22}). Evaluation owns
 * official scoring. The handoff is triggered only by the winning submission transition, so it is
 * never duplicated.
 */
@Service
public class EvaluationHandoffService {

    public static final String JOB_TYPE = "EVALUATION";
    public static final String REFERENCE_TYPE = "STUDENT_ATTEMPT";

    private final BackgroundJobRepository jobRepository;

    public EvaluationHandoffService(BackgroundJobRepository jobRepository) {
        this.jobRepository = jobRepository;
    }

    @Transactional
    public BackgroundJob enqueue(UUID attemptId) {
        List<BackgroundJob> existing = jobRepository
                .findByJobTypeAndReferenceTypeAndReferenceId(JOB_TYPE, REFERENCE_TYPE, attemptId);
        if (!existing.isEmpty()) {
            return existing.get(0);
        }
        BackgroundJob job = new BackgroundJob();
        job.setJobType(JOB_TYPE);
        job.setReferenceType(REFERENCE_TYPE);
        job.setReferenceId(attemptId);
        job.setInitiatedBy(CurrentAuditorProvider.SYSTEM_ACTOR_ID);
        job.setStatus(BackgroundJobStatus.QUEUED);
        job.setParameters("{\"attemptId\":\"" + attemptId + "\"}");
        job.setQueuedOn(Instant.now());
        job.setProgressCurrent(0);
        job.setProgressTotal(1);
        job.setAttemptCount(0);
        return jobRepository.save(job);
    }
}
