package com.company.exam.evaluation.strategy;

import com.company.exam.evaluation.scoring.QuestionEvaluationContext;
import com.company.exam.evaluation.scoring.QuestionEvaluationOutcome;

/**
 * The template-specific question evaluation strategy boundary
 * ({@code specs/15-P6-Evaluation-V1.5.md §11, §12}).
 *
 * <p>Template-specific response interpretation is separated from global assessment score
 * aggregation. There is no single class that hard-codes every future template.
 */
public interface QuestionEvaluator {

    /** Whether this evaluator handles the given question-template code. */
    boolean supports(String templateCode);

    /** Determines the raw outcome of one question. Pure: no database or HTTP dependency. */
    QuestionEvaluationOutcome evaluate(QuestionEvaluationContext context);
}
