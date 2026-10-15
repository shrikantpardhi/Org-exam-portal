package com.company.exam.evaluation.strategy;

import com.company.exam.evaluation.exception.ScoringConfigurationInvalidException;
import com.company.exam.evaluation.scoring.QuestionEvaluationContext;
import com.company.exam.evaluation.scoring.QuestionEvaluationOutcome;
import com.company.exam.evaluation.scoring.QuestionOutcomeState;
import com.company.exam.evaluation.scoring.QuestionScoringPolicy;
import java.math.BigDecimal;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Numeric objective evaluation for INTEGER and DECIMAL templates
 * ({@code specs/15-P6-Evaluation-V1.5.md §22, §23}).
 *
 * <p>A numeric template is compared numerically with {@link BigDecimal#compareTo}; scale and
 * trailing zeros do not change correctness. A response that is not a number is an explicit invalid
 * response; an authoritative answer that is not a number is an invalid scoring configuration.
 */
@Component
public class NumericAnswerEvaluator implements QuestionEvaluator {

    private static final Set<String> TEMPLATES = Set.of("INTEGER", "DECIMAL");

    private final QuestionScoringPolicy scoringPolicy;

    public NumericAnswerEvaluator(QuestionScoringPolicy scoringPolicy) {
        this.scoringPolicy = scoringPolicy;
    }

    @Override
    public boolean supports(String templateCode) {
        return templateCode != null && TEMPLATES.contains(templateCode);
    }

    @Override
    public QuestionEvaluationOutcome evaluate(QuestionEvaluationContext context) {
        return scoringPolicy.apply(context, determine(context));
    }

    private QuestionOutcomeState determine(QuestionEvaluationContext context) {
        String response = context.response() == null ? "" : context.response().trim();
        if (response.isEmpty()) {
            return QuestionOutcomeState.UNANSWERED;
        }
        if (context.correctAnswerText() == null) {
            throw new ScoringConfigurationInvalidException(
                    "Numeric question has no authoritative answer text in the answer key");
        }
        BigDecimal correct = parse(context.correctAnswerText());
        if (correct == null) {
            throw new ScoringConfigurationInvalidException(
                    "Numeric answer key value is not a valid number");
        }
        BigDecimal answered = parse(response);
        if (answered == null) {
            return QuestionOutcomeState.INVALID_RESPONSE;
        }
        return answered.compareTo(correct) == 0 ? QuestionOutcomeState.CORRECT : QuestionOutcomeState.INCORRECT;
    }

    private BigDecimal parse(String value) {
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException notANumber) {
            return null;
        }
    }
}
