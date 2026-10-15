package com.company.exam.evaluation.scoring;

import com.company.exam.evaluation.entity.QuestionOutcome;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * The immutable per-question evaluation input ({@code specs/15-P6-Evaluation-V1.5.md §40, §75}).
 *
 * <p>It carries only primitive scoring data resolved from the frozen sources: the exact snapshot
 * marks ({@code attempt_question}), the student response, the authoritative
 * {@code answer_key_detail} values, and the option identity map. No JPA entity is passed into the
 * pure calculation layer.
 */
public record QuestionEvaluationContext(
        UUID questionVersionId,
        String templateCode,
        BigDecimal questionMarks,
        BigDecimal questionNegativeMarks,
        String response,
        QuestionOutcome answerOutcome,
        UUID correctOptionId,
        String correctAnswerText,
        BigDecimal overrideMarks,
        BigDecimal overrideNegativeMarks,
        Map<UUID, String> optionKeysByOptionId) {

    public QuestionEvaluationContext {
        optionKeysByOptionId = optionKeysByOptionId == null ? Map.of() : Map.copyOf(optionKeysByOptionId);
    }
}
