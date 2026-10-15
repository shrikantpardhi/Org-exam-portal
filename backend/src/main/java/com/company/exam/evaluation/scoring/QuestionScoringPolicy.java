package com.company.exam.evaluation.scoring;

import com.company.exam.evaluation.entity.QuestionOutcome;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

/**
 * Applies the assessment scoring contract to a raw question outcome
 * ({@code specs/15-P6-Evaluation-V1.5.md §16, §19, §20, §21}).
 *
 * <p>The evaluator strategy decides the raw determination (CORRECT / INCORRECT / UNANSWERED). This
 * component decides how that determination contributes to the final score, in one place, so that
 * bonus, dropped, and override handling is never duplicated:
 * <ul>
 *   <li>{@code BONUS} awards the configured marks regardless of the response ({@code §19}).</li>
 *   <li>{@code DROPPED} contributes 0 and ignores the response ({@code §20}).</li>
 *   <li>overrides on the detail supersede the snapshot marks when present ({@code §21}).</li>
 *   <li>the total is never clamped at zero ({@code §17}).</li>
 * </ul>
 *
 * <p>All arithmetic is {@link BigDecimal}; no floating-point primitive is used ({@code §33, §74}).
 */
@Component
public class QuestionScoringPolicy {

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    public QuestionEvaluationOutcome apply(QuestionEvaluationContext context, QuestionOutcomeState rawState) {
        BigDecimal marks = override(context.overrideMarks(), context.questionMarks());
        BigDecimal negativeMarks = override(context.overrideNegativeMarks(), context.questionNegativeMarks());

        QuestionOutcome outcome = context.answerOutcome() == null ? QuestionOutcome.NORMAL : context.answerOutcome();
        return switch (outcome) {
            case BONUS -> outcome(context, QuestionOutcomeState.BONUS, marks, marks, ZERO);
            case DROPPED -> outcome(context, QuestionOutcomeState.DROPPED, ZERO, ZERO, ZERO);
            case NORMAL -> normal(context, rawState, marks, negativeMarks);
        };
    }

    private QuestionEvaluationOutcome normal(QuestionEvaluationContext context, QuestionOutcomeState rawState,
            BigDecimal marks, BigDecimal negativeMarks) {
        return switch (rawState) {
            case UNANSWERED -> outcome(context, QuestionOutcomeState.UNANSWERED, ZERO, marks, ZERO);
            case CORRECT -> outcome(context, QuestionOutcomeState.CORRECT, marks, marks, ZERO);
            case INCORRECT -> {
                BigDecimal applied = negativeMarks == null ? ZERO : negativeMarks.negate();
                yield outcome(context, QuestionOutcomeState.INCORRECT, applied, marks, negativeMarks);
            }
            // INVALID_RESPONSE and UNSUPPORTED are passed through: the aggregate calculation fails
            // the evaluation rather than silently scoring them (§67, §72).
            default -> outcome(context, rawState, ZERO, marks, ZERO);
        };
    }

    private BigDecimal override(BigDecimal override, BigDecimal snapshotValue) {
        if (override != null) {
            return override;
        }
        return snapshotValue == null ? ZERO : snapshotValue;
    }

    private QuestionEvaluationOutcome outcome(QuestionEvaluationContext context, QuestionOutcomeState state,
            BigDecimal awarded, BigDecimal maximum, BigDecimal negativeApplied) {
        return new QuestionEvaluationOutcome(context.questionVersionId(), state, awarded, maximum, negativeApplied);
    }
}
