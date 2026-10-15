package com.company.exam.evaluation.scoring;

import java.math.BigDecimal;
import java.util.List;

/**
 * The aggregate evaluation output ({@code specs/15-P6-Evaluation-V1.5.md §10, §73}).
 *
 * <p>{@code obtainedMarks} and {@code percentage} are the official rounded values;
 * {@code maximumMarks} is the sum of the per-question maximums and is retained for audit
 * ({@code §18}).
 */
public record EvaluationOutcome(
        BigDecimal obtainedMarks,
        BigDecimal percentage,
        BigDecimal maximumMarks,
        List<QuestionEvaluationOutcome> questionOutcomes) {

    public EvaluationOutcome {
        questionOutcomes = questionOutcomes == null ? List.of() : List.copyOf(questionOutcomes);
    }
}
