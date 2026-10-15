package com.company.exam.evaluation.strategy;

import com.company.exam.evaluation.exception.ScoringConfigurationInvalidException;
import com.company.exam.evaluation.scoring.QuestionEvaluationContext;
import com.company.exam.evaluation.scoring.QuestionEvaluationOutcome;
import com.company.exam.evaluation.scoring.QuestionOutcomeState;
import com.company.exam.evaluation.scoring.QuestionScoringPolicy;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Short-text objective evaluation for FILL_IN_THE_BLANK ({@code specs/15-P6-Evaluation-V1.5.md §23}).
 *
 * <p>The only normalization rule enforced is the one the template explicitly supports: trimmed,
 * internal-whitespace-collapsed, case-insensitive comparison. No other normalization is invented.
 */
@Component
public class TextAnswerEvaluator implements QuestionEvaluator {

    private static final Set<String> TEMPLATES = Set.of("FILL_IN_THE_BLANK");

    private final QuestionScoringPolicy scoringPolicy;

    public TextAnswerEvaluator(QuestionScoringPolicy scoringPolicy) {
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
                    "Text question has no authoritative answer text in the answer key");
        }
        return AnswerNormalizer.text(context.response()).equals(AnswerNormalizer.text(context.correctAnswerText()))
                ? QuestionOutcomeState.CORRECT
                : QuestionOutcomeState.INCORRECT;
    }
}
