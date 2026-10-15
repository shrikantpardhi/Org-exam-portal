package com.company.exam.evaluation.validation;

import com.company.exam.assessment.api.AssessmentDefinitionCatalog;
import com.company.exam.content.api.ContentCatalog;
import com.company.exam.evaluation.dto.EvaluationDtos;
import com.company.exam.evaluation.entity.QuestionOutcome;
import com.company.exam.evaluation.exception.AnswerKeyDetailInvalidException;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Answer-key and answer-key-detail validation ({@code specs/15-P6-Evaluation-V1.5.md §29, §120, §121}).
 *
 * <p>It enforces the frozen answer_key_detail invariants and verifies that a question version belongs
 * to the bound assessment version and that a referenced option belongs to the same question version.
 */
@Component
public class AnswerKeyValidator {

    private final AssessmentDefinitionCatalog assessmentCatalog;
    private final ContentCatalog contentCatalog;

    public AnswerKeyValidator(AssessmentDefinitionCatalog assessmentCatalog, ContentCatalog contentCatalog) {
        this.assessmentCatalog = assessmentCatalog;
        this.contentCatalog = contentCatalog;
    }

    /** Parses the controlled outcome vocabulary, rejecting anything outside NORMAL/BONUS/DROPPED. */
    public QuestionOutcome requireOutcome(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new AnswerKeyDetailInvalidException("questionOutcome", "questionOutcome is required");
        }
        try {
            return QuestionOutcome.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException invalid) {
            throw new AnswerKeyDetailInvalidException("questionOutcome",
                    "questionOutcome must be NORMAL, BONUS, or DROPPED");
        }
    }

    /**
     * Validates one detail mapping against the frozen answer_key_detail contract
     * ({@code §5.2, §30, §121}).
     */
    public void validateMapping(EvaluationDtos.AnswerKeyDetailRequest request, QuestionOutcome outcome) {
        UUID correctOptionId = request.correctOptionId();
        String correctAnswerText = request.correctAnswerText();
        boolean hasText = correctAnswerText != null && !correctAnswerText.isBlank();

        if (outcome == QuestionOutcome.NORMAL) {
            if (correctOptionId == null && !hasText) {
                throw new AnswerKeyDetailInvalidException("correctOptionId",
                        "A NORMAL detail requires either correctOptionId or correctAnswerText");
            }
            if (correctOptionId != null) {
                requireOptionInQuestion(correctOptionId, request.questionVersionId());
            }
        } else if (correctOptionId != null || correctAnswerText != null) {
            throw new AnswerKeyDetailInvalidException("correctOptionId",
                    "A BONUS or DROPPED detail must have a null correctOptionId and correctAnswerText");
        }
    }

    /** Verifies the question version is an evaluable question of the bound assessment version. */
    public void requireQuestionInAssessmentVersion(UUID assessmentVersionId, UUID questionVersionId) {
        List<AssessmentDefinitionCatalog.AssessmentQuestionView> questions =
                assessmentCatalog.questions(assessmentVersionId);
        boolean present = questions.stream()
                .anyMatch(question -> question.questionVersionId().equals(questionVersionId));
        if (!present) {
            throw new AnswerKeyDetailInvalidException("questionVersionId",
                    "The question version is not part of the bound assessment version");
        }
    }

    private void requireOptionInQuestion(UUID optionId, UUID questionVersionId) {
        boolean present = contentCatalog.runtimeQuestion(questionVersionId).options().stream()
                .anyMatch(option -> option.optionId().equals(optionId));
        if (!present) {
            throw new AnswerKeyDetailInvalidException("correctOptionId",
                    "The correct option does not belong to the question version");
        }
    }
}
