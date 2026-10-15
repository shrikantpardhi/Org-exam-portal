package com.company.exam.evaluation.scoring;

/**
 * The implementation-level question outcome state ({@code specs/15-P6-Evaluation-V1.5.md §72}).
 *
 * <p>These are application concepts, not frozen database enums. Only the aggregate score is
 * persisted in P6.
 */
public enum QuestionOutcomeState {
    CORRECT,
    INCORRECT,
    UNANSWERED,
    INVALID_RESPONSE,
    BONUS,
    DROPPED,
    UNSUPPORTED
}
