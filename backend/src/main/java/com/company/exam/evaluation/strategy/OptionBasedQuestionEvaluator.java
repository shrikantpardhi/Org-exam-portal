package com.company.exam.evaluation.strategy;

import com.company.exam.evaluation.scoring.QuestionEvaluationContext;
import com.company.exam.evaluation.scoring.QuestionEvaluationOutcome;
import com.company.exam.evaluation.scoring.QuestionOutcomeState;
import com.company.exam.evaluation.scoring.QuestionScoringPolicy;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Option-based objective evaluation ({@code specs/15-P6-Evaluation-V1.5.md §13, §22}).
 *
 * <p>The authoritative answer is resolved from {@code answer_key_detail} only. The student response
 * is matched against the exact option identity (option key or option id) of the snapshot; a response
 * that names no known option is an explicit invalid response rather than a silent incorrect
 * ({@code §23, §67}). {@code question_option.is_correct} is never consulted ({@code §113}).
 */
@Component
public class OptionBasedQuestionEvaluator implements QuestionEvaluator {

    private static final Set<String> TEMPLATES = Set.of("SINGLE_CHOICE", "MULTIPLE_CHOICE", "TRUE_FALSE");

    private final QuestionScoringPolicy scoringPolicy;

    public OptionBasedQuestionEvaluator(QuestionScoringPolicy scoringPolicy) {
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
        String response = AnswerNormalizer.identity(context.response());
        if (response.isEmpty()) {
            return QuestionOutcomeState.UNANSWERED;
        }
        if (context.correctOptionId() != null) {
            if (!knownIdentities(context).contains(response)) {
                return QuestionOutcomeState.INVALID_RESPONSE;
            }
            return correctIdentities(context).contains(response)
                    ? QuestionOutcomeState.CORRECT
                    : QuestionOutcomeState.INCORRECT;
        }
        // An option-based question whose authoritative answer is free text (no option reference).
        if (context.correctAnswerText() != null) {
            return AnswerNormalizer.text(context.response()).equals(AnswerNormalizer.text(context.correctAnswerText()))
                    ? QuestionOutcomeState.CORRECT
                    : QuestionOutcomeState.INCORRECT;
        }
        return QuestionOutcomeState.INVALID_RESPONSE;
    }

    private Set<String> knownIdentities(QuestionEvaluationContext context) {
        Set<String> identities = new LinkedHashSet<>();
        for (Map.Entry<UUID, String> option : context.optionKeysByOptionId().entrySet()) {
            identities.add(AnswerNormalizer.identity(option.getValue()));
            identities.add(AnswerNormalizer.identity(option.getKey().toString()));
        }
        return identities;
    }

    private Set<String> correctIdentities(QuestionEvaluationContext context) {
        Set<String> identities = new LinkedHashSet<>();
        identities.add(AnswerNormalizer.identity(context.correctOptionId().toString()));
        String key = context.optionKeysByOptionId().get(context.correctOptionId());
        if (key != null) {
            identities.add(AnswerNormalizer.identity(key));
        }
        return identities;
    }
}
