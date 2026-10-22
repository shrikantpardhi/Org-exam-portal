package com.company.exam.delivery.service;

import com.company.exam.common.audit.CurrentAuditorProvider;
import com.company.exam.common.audit.SecurityEventLogger;
import com.company.exam.common.exception.InvalidStateException;
import com.company.exam.common.exception.NotFoundException;
import com.company.exam.delivery.api.AttemptCompletionContract;
import com.company.exam.delivery.entity.AttemptStatus;
import com.company.exam.delivery.entity.StudentAttempt;
import com.company.exam.delivery.repository.StudentAttemptRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Default {@link AttemptCompletionContract} ({@code specs/14-P5-Delivery-V1.5.md §8, §13, §14}).
 *
 * <p>The transition is a conditional update, so the number of affected rows is the ownership signal:
 * exactly one caller moves the attempt from {@code SUBMITTED} to {@code EVALUATED}. A retry that
 * finds the attempt already {@code EVALUATED} with the same score is a no-op, which keeps the
 * completion contract idempotent without weakening the state machine.
 */
@Service
public class AttemptCompletionService implements AttemptCompletionContract {

    private final StudentAttemptRepository attemptRepository;
    private final SecurityEventLogger audit;
    private final CurrentAuditorProvider currentAuditor;

    public AttemptCompletionService(StudentAttemptRepository attemptRepository, SecurityEventLogger audit,
            CurrentAuditorProvider currentAuditor) {
        this.attemptRepository = attemptRepository;
        this.audit = audit;
        this.currentAuditor = currentAuditor;
    }

    @Override
    @Transactional
    public void markEvaluated(UUID attemptId, BigDecimal totalScore, Instant evaluatedOn) {
        Instant now = evaluatedOn == null ? Instant.now() : evaluatedOn;
        UUID actorId = currentAuditor.getCurrentAuditor().orElse(CurrentAuditorProvider.SYSTEM_ACTOR_ID);

        int updated = attemptRepository.markEvaluated(attemptId, AttemptStatus.SUBMITTED, AttemptStatus.EVALUATED,
                totalScore, actorId, now);
        if (updated == 1) {
            audit.audit(actorId, "EVALUATE", "StudentAttempt", attemptId,
                    "Attempt completed as EVALUATED with total score " + totalScore);
            return;
        }

        // No row moved: the attempt is already EVALUATED (a retried evaluation) or it is in a state
        // that may not be completed. Only the identical completion is accepted as a no-op.
        StudentAttempt attempt = attemptRepository.findById(attemptId)
                .orElseThrow(() -> NotFoundException.of("Attempt", attemptId));
        if (attempt.getAttemptStatus() == AttemptStatus.EVALUATED
                && attempt.getTotalScore() != null
                && totalScore != null
                && attempt.getTotalScore().compareTo(totalScore) == 0) {
            return;
        }
        throw new InvalidStateException("Only a SUBMITTED attempt can be completed as EVALUATED");
    }

    @Override
    @Transactional
    public void refreshScore(UUID attemptId, BigDecimal totalScore, Instant evaluatedOn) {
        Instant now = evaluatedOn == null ? Instant.now() : evaluatedOn;
        UUID actorId = currentAuditor.getCurrentAuditor().orElse(CurrentAuditorProvider.SYSTEM_ACTOR_ID);

        int updated = attemptRepository.refreshEvaluatedScore(attemptId, AttemptStatus.EVALUATED, totalScore,
                actorId, now);
        if (updated == 1) {
            audit.audit(actorId, "EVALUATE", "StudentAttempt", attemptId,
                    "Attempt total score refreshed to " + totalScore);
            return;
        }

        // No row moved: the attempt is not completed, or it already carries exactly this score.
        StudentAttempt attempt = attemptRepository.findById(attemptId)
                .orElseThrow(() -> NotFoundException.of("Attempt", attemptId));
        if (attempt.getAttemptStatus() == AttemptStatus.EVALUATED
                && attempt.getTotalScore() != null
                && totalScore != null
                && attempt.getTotalScore().compareTo(totalScore) == 0) {
            return;
        }
        throw new InvalidStateException("Only an EVALUATED attempt can have its official score refreshed");
    }
}
