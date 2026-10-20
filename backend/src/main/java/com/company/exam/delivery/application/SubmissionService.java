package com.company.exam.delivery.application;

import com.company.exam.common.audit.CurrentAuditorProvider;
import com.company.exam.common.exception.InvalidStateException;
import com.company.exam.delivery.entity.AttemptStatus;
import com.company.exam.delivery.entity.StudentAttempt;
import com.company.exam.delivery.repository.StudentAttemptRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Idempotent submission ({@code specs/14-P5-Delivery-V1.5.md §63-§69}).
 *
 * <p>Submission is a state transition, not client-side cleanup. Two or more simultaneous submits
 * produce one logical submission: the first request performs the conditional
 * {@code IN_PROGRESS -> SUBMITTED} transition, and later requests return the same submitted state
 * with the same {@code submitted_on} timestamp. It never resurrects an attempt to IN_PROGRESS,
 * never double-ends a session, and never duplicates the evaluation trigger.
 */
@Service
public class SubmissionService {

    private final StudentAttemptRepository attemptRepository;
    private final AssessmentSessionService sessionService;
    private final EvaluationHandoffService evaluationHandoffService;
    private final AttemptEligibilityService eligibilityService;
    private final com.company.exam.delivery.observability.DeliveryMetrics metrics;

    public SubmissionService(StudentAttemptRepository attemptRepository, AssessmentSessionService sessionService,
            EvaluationHandoffService evaluationHandoffService, AttemptEligibilityService eligibilityService,
            com.company.exam.delivery.observability.DeliveryMetrics metrics) {
        this.attemptRepository = attemptRepository;
        this.sessionService = sessionService;
        this.evaluationHandoffService = evaluationHandoffService;
        this.eligibilityService = eligibilityService;
        this.metrics = metrics;
    }

    @Transactional
    public DeliveryDtos.SubmitResponse submit(UUID attemptId, UUID userId) {
        return metrics.submission(() -> submitAttempt(attemptId, userId));
    }

    private DeliveryDtos.SubmitResponse submitAttempt(UUID attemptId, UUID userId) {
        StudentAttempt attempt = eligibilityService.requireOwned(attemptId, userId);
        if (attempt.getAttemptStatus() == AttemptStatus.SUBMITTED
                || attempt.getAttemptStatus() == AttemptStatus.EVALUATED) {
            return new DeliveryDtos.SubmitResponse(attempt.getId(), attempt.getAttemptStatus().name(),
                    attempt.getSubmittedOn());
        }
        if (attempt.getAttemptStatus() != AttemptStatus.IN_PROGRESS) {
            throw new InvalidStateException("Attempt is not in progress");
        }
        return finalize(attemptId, userId);
    }

    /**
     * The expiry finalization ({@code §56}): the same server submission path a manual submit uses,
     * executed under the system actor because nobody is present to press Submit.
     *
     * @return {@code true} when this caller performed the finalization
     */
    @Transactional
    public boolean finalizeExpired(UUID attemptId) {
        try {
            finalize(attemptId, CurrentAuditorProvider.SYSTEM_ACTOR_ID);
            return true;
        } catch (InvalidStateException lostRace) {
            // Another finalization won; there is nothing to do and nothing to report as a failure.
            return false;
        }
    }

    /**
     * The one finalization path, shared by manual submit and expiry auto-submit.
     *
     * <p>The conditional transition is the ownership signal: exactly one caller moves the attempt from
     * IN_PROGRESS to SUBMITTED, and only that caller ends the session and hands off to evaluation, so
     * a manual submit racing an expiry produces one logical submission and one evaluation job.
     */
    private DeliveryDtos.SubmitResponse finalize(UUID attemptId, UUID actorId) {
        Instant now = Instant.now();
        int updated = attemptRepository.markSubmitted(attemptId, AttemptStatus.IN_PROGRESS, AttemptStatus.SUBMITTED,
                actorId, now);
        if (updated == 0) {
            StudentAttempt current = attemptRepository.findById(attemptId).orElseThrow();
            if (current.getAttemptStatus() == AttemptStatus.SUBMITTED
                    || current.getAttemptStatus() == AttemptStatus.EVALUATED) {
                return new DeliveryDtos.SubmitResponse(current.getId(), current.getAttemptStatus().name(),
                        current.getSubmittedOn());
            }
            throw new InvalidStateException("Attempt could not be submitted");
        }

        StudentAttempt submitted = attemptRepository.findById(attemptId).orElseThrow();
        sessionService.endActiveForAttempt(attemptId, now);
        evaluationHandoffService.enqueue(attemptId);
        metrics.attemptSubmitted();
        return new DeliveryDtos.SubmitResponse(submitted.getId(), AttemptStatus.SUBMITTED.name(),
                submitted.getSubmittedOn());
    }
}
