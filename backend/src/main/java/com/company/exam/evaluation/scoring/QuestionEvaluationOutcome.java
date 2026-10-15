package com.company.exam.evaluation.scoring;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * The outcome of evaluating one snapshot question ({@code specs/15-P6-Evaluation-V1.5.md §71}).
 *
 * <p>This is an application object, not a database table.
 */
public record QuestionEvaluationOutcome(
        UUID questionVersionId,
        QuestionOutcomeState state,
        BigDecimal awardedMarks,
        BigDecimal maximumMarks,
        BigDecimal negativeMarksApplied) {

    /** A question that counts toward attempted/accuracy denominators (never BONUS or DROPPED). */
    public boolean attempted() {
        return state == QuestionOutcomeState.CORRECT || state == QuestionOutcomeState.INCORRECT;
    }
}
