package com.company.exam.evaluation.scoring;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * The immutable assessment-level evaluation context ({@code specs/15-P6-Evaluation-V1.5.md §40, §75}).
 *
 * <p>The scoring context is immutable after evaluation starts; marks, answer key, question mapping,
 * and scoring configuration are not mutated during a calculation.
 */
public record EvaluationContext(
        UUID attemptId,
        UUID assessmentVersionId,
        BigDecimal publishedTotalMarks,
        List<QuestionEvaluationContext> questions) {

    public EvaluationContext {
        questions = questions == null ? List.of() : List.copyOf(questions);
    }
}
