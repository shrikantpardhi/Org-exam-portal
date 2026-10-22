package com.company.exam.delivery.application;

import com.company.exam.common.audit.CurrentAuditorProvider;
import com.company.exam.common.job.BackgroundJob;
import com.company.exam.common.job.BackgroundJobRepository;
import com.company.exam.common.job.BackgroundJobStatus;
import com.company.exam.delivery.api.SubmitAnswerKeyResolver;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Durable, idempotent evaluation handoff ({@code specs/14-P5-Delivery-V1.5.md §67, §66}).
 *
 * <p>P5 stops at {@code StudentAttempt = SUBMITTED} and enqueues a {@code background_job} of type
 * {@code EVALUATION} using the system actor identity ({@code §14 invariant 22}). Evaluation owns
 * official scoring. The handoff is triggered only by the winning submission transition, so it is
 * never duplicated.
 *
 * <p>The handoff carries an explicit {@code answerKeyId} resolved at submission time
 * ({@code specs/15-P6-Evaluation-V1.5.md §25, §26}). When no published answer key exists for the
 * attempt's {@code AssessmentVersion}, no job is enqueued and an operational warning is recorded,
 * rather than defaulting to a "current published key".
 */
@Service
public class EvaluationHandoffService {

    private static final Logger log = LoggerFactory.getLogger(EvaluationHandoffService.class);

    public static final String JOB_TYPE = com.company.exam.common.job.JobTypes.EVALUATION;
    public static final String REFERENCE_TYPE = "STUDENT_ATTEMPT";

    private final BackgroundJobRepository jobRepository;
    private final SubmitAnswerKeyResolver answerKeyResolver;

    public EvaluationHandoffService(BackgroundJobRepository jobRepository, SubmitAnswerKeyResolver answerKeyResolver) {
        this.jobRepository = jobRepository;
        this.answerKeyResolver = answerKeyResolver;
    }

    /**
     * Enqueues the evaluation handoff for a freshly submitted attempt.
     *
     * <p>The job carries both the attempt id and the resolved {@code answerKeyId}. When no published
     * answer key could be resolved for the assessment version, no job is created and an operational
     * warning is logged; the attempt is still {@code SUBMITTED} and can be evaluated later through
     * the explicit {@code POST /attempts/{id}/evaluate} endpoint.
     *
     * @return the enqueued job, or empty when no published answer key could be resolved
     */
    @Transactional
    public Optional<BackgroundJob> enqueue(UUID attemptId, UUID assessmentVersionId) {
        List<BackgroundJob> existing = jobRepository
                .findByJobTypeAndReferenceTypeAndReferenceId(JOB_TYPE, REFERENCE_TYPE, attemptId);
        if (!existing.isEmpty()) {
            return Optional.of(existing.get(0));
        }
        Optional<UUID> answerKeyId = answerKeyResolver.resolveForSubmit(assessmentVersionId);
        if (answerKeyId.isEmpty()) {
            log.warn("No published answer key for assessment version {}; submitted attempt {} will not be "
                    + "evaluated automatically. Publish an answer key for this assessment version and trigger "
                    + "evaluation explicitly (specs/15-P6 §26).", assessmentVersionId, attemptId);
            return Optional.empty();
        }
        BackgroundJob job = new BackgroundJob();
        job.setJobType(JOB_TYPE);
        job.setReferenceType(REFERENCE_TYPE);
        job.setReferenceId(attemptId);
        job.setInitiatedBy(CurrentAuditorProvider.SYSTEM_ACTOR_ID);
        job.setStatus(BackgroundJobStatus.QUEUED);
        job.setParameters("{\"attemptId\":\"" + attemptId + "\",\"answerKeyId\":\"" + answerKeyId.get() + "\"}");
        job.setQueuedOn(Instant.now());
        job.setProgressCurrent(0);
        job.setProgressTotal(1);
        job.setAttemptCount(0);
        return Optional.of(jobRepository.save(job));
    }
}
