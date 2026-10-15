package com.company.exam.evaluation.strategy;

import com.company.exam.evaluation.exception.UnsupportedQuestionTypeException;
import com.company.exam.evaluation.scoring.QuestionEvaluationContext;
import com.company.exam.evaluation.scoring.QuestionEvaluationOutcome;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Dispatches a question to its registered evaluator strategy
 * ({@code specs/15-P6-Evaluation-V1.5.md §11, §42}).
 *
 * <p>An unsupported template fails to an explicit, auditable state rather than silently guessing
 * ({@code §23, §122}).
 */
@Component
public class QuestionEvaluatorRegistry {

    private final List<QuestionEvaluator> evaluators;

    public QuestionEvaluatorRegistry(List<QuestionEvaluator> evaluators) {
        this.evaluators = List.copyOf(evaluators);
    }

    /** Returns the evaluator for the template code, or throws a controlled unsupported error. */
    public QuestionEvaluator require(String templateCode) {
        return evaluators.stream()
                .filter(evaluator -> evaluator.supports(templateCode))
                .findFirst()
                .orElseThrow(() -> new UnsupportedQuestionTypeException(
                        "No evaluator strategy exists for question template: " + templateCode));
    }

    public QuestionEvaluationOutcome evaluate(QuestionEvaluationContext context) {
        return require(context.templateCode()).evaluate(context);
    }
}
