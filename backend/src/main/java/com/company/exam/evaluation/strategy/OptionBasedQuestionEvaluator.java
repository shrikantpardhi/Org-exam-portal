package com.company.exam.evaluation.strategy;

import com.company.exam.evaluation.scoring.QuestionEvaluationContext;
import com.company.exam.evaluation.scoring.QuestionEvaluationOutcome;
import com.company.exam.evaluation.scoring.QuestionOutcomeState;
import com.company.exam.evaluation.scoring.QuestionScoringPolicy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
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
 *
 * <p>Single-correct and multi-correct questions are both supported (ADR-0025). For multi-correct
 * questions the student must select exactly the authoritative option set; any other non-empty
 * selection (including a partial one) is incorrect (all-or-nothing, ADR-0025).
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
        String rawResponse = context.response();
        if (rawResponse == null || rawResponse.trim().isEmpty()) {
            return QuestionOutcomeState.UNANSWERED;
        }

        // Resolve the authoritative correct option set (child rows take precedence over the single
        // correct_option_id, ADR-0025).
        Set<UUID> correctIds = new LinkedHashSet<>();
        if (context.correctOptionIds() != null) {
            correctIds.addAll(context.correctOptionIds());
        }
        if (context.correctOptionId() != null) {
            correctIds.add(context.correctOptionId());
        }

        if (!correctIds.isEmpty()) {
            Set<UUID> selected = resolveSelected(rawResponse, context);
            if (selected == null) {
                return QuestionOutcomeState.INVALID_RESPONSE;
            }
            if (selected.isEmpty()) {
                return QuestionOutcomeState.UNANSWERED;
            }
            return selected.equals(correctIds)
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

    private Set<UUID> resolveSelected(String rawResponse, QuestionEvaluationContext context) {
        List<String> tokens = tokenize(rawResponse);
        Set<UUID> selected = new LinkedHashSet<>();
        for (String token : tokens) {
            UUID resolved = resolveOptionId(token, context);
            if (resolved == null) {
                return null;
            }
            selected.add(resolved);
        }
        return selected;
    }

    private UUID resolveOptionId(String token, QuestionEvaluationContext context) {
        String norm = AnswerNormalizer.identity(token);
        if (norm.isEmpty()) {
            return null;
        }
        for (Map.Entry<UUID, String> entry : context.optionKeysByOptionId().entrySet()) {
            if (AnswerNormalizer.identity(entry.getKey().toString()).equals(norm)) {
                return entry.getKey();
            }
            if (entry.getValue() != null
                    && AnswerNormalizer.identity(entry.getValue()).equals(norm)) {
                return entry.getKey();
            }
        }
        return null;
    }

    private List<String> tokenize(String rawResponse) {
        String trimmed = rawResponse.trim();
        if (trimmed.isEmpty()) {
            return List.of();
        }
        if (trimmed.contains(",") || trimmed.contains(";") || trimmed.contains("|")) {
            List<String> tokens = new ArrayList<>();
            for (String part : trimmed.split("[,;|]")) {
                if (!part.trim().isEmpty()) {
                    tokens.add(part);
                }
            }
            return tokens;
        }
        if (trimmed.contains(" ") || trimmed.contains("\t") || trimmed.contains("\n")) {
            return Arrays.asList(trimmed.split("\\s+"));
        }
        return List.of(trimmed);
    }

    private Set<String> knownIdentities(QuestionEvaluationContext context) {
        Set<String> identities = new LinkedHashSet<>();
        for (Map.Entry<UUID, String> option : context.optionKeysByOptionId().entrySet()) {
            identities.add(AnswerNormalizer.identity(option.getValue()));
            identities.add(AnswerNormalizer.identity(option.getKey().toString()));
        }
        return identities;
    }
}