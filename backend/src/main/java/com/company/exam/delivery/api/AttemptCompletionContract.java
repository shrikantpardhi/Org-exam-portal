package com.company.exam.delivery.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * The Delivery-owned completion contract consumed by Evaluation
 * ({@code specs/14-P5-Delivery-V1.5.md §8, §13} with {@code specs/15-P6-Evaluation-V1.5.md §9, §10,
 * §53}).
 *
 * <p>{@code student_attempt.attempt_status} and {@code student_attempt.total_score} are
 * Delivery-owned columns. Evaluation owns the official score but must not write Delivery state
 * directly, so it reports the completed attempt through this contract and Delivery performs the
 * transition. Evaluation never touches a Delivery repository.
 *
 * <p>The transition is the {@code SUBMITTED -> EVALUATED} edge of the frozen attempt state machine.
 * It is expressed as a conditional update, so a retry after a partially applied change is safe.
 */
public interface AttemptCompletionContract {

    /**
     * Advances a {@code SUBMITTED} attempt to {@code EVALUATED} and records the official total
     * score.
     *
     * <p>Calling it again for an attempt that is already {@code EVALUATED} with the same total
     * score is a no-op, so a retried evaluation does not fail and does not duplicate the audit
     * event. Calling it for an attempt in any other state is rejected.
     *
     * @param attemptId  the completed attempt
     * @param totalScore the official total score, the same value as the Result's {@code obtained_marks}
     * @param evaluatedOn the moment the evaluation completed
     * @throws com.company.exam.common.exception.InvalidStateException when the attempt is not
     *         {@code SUBMITTED} and is not already {@code EVALUATED} with the same score
     */
    void markEvaluated(UUID attemptId, BigDecimal totalScore, Instant evaluatedOn);

    /**
     * Refreshes the official total score of an attempt that is already {@code EVALUATED}.
     *
     * <p>A controlled re-evaluation supersedes the previous Result and creates a new one with a new
     * {@code obtained_marks} ({@code specs/15-P6-Evaluation-V1.5.md §46, §52, §56}). The
     * Delivery-owned {@code total_score} must follow the current official score, so this refresh
     * keeps the attempt consistent with its current Result.
     *
     * <p>It is idempotent: refreshing an attempt whose score already equals the given value is a
     * no-op and emits no second audit event.
     *
     * @param attemptId  the completed attempt
     * @param totalScore the current official total score, the same value as the current Result's
     *        {@code obtained_marks}
     * @param evaluatedOn the moment the current evaluation completed
     * @throws com.company.exam.common.exception.InvalidStateException when the attempt is in a state
     *         that may not carry an official score
     */
    void refreshScore(UUID attemptId, BigDecimal totalScore, Instant evaluatedOn);
}
