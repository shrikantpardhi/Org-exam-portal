package com.company.exam.evaluation.scoring;

import com.company.exam.evaluation.exception.InvalidResponseDataException;
import com.company.exam.evaluation.strategy.QuestionEvaluatorRegistry;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * The pure scoring calculation layer ({@code specs/15-P6-Evaluation-V1.5.md §31, §63, §73, §74}).
 *
 * <p>It is free of database and HTTP dependencies, which makes evaluation deterministic, unit
 * testable, and safe to re-run. Every official number is {@link BigDecimal}; no binary floating
 * point is used. For identical inputs and policy versions the output is identical ({@code §31}).
 */
@Component
public class EvaluationCalculator {

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final QuestionEvaluatorRegistry evaluatorRegistry;

    public EvaluationCalculator(QuestionEvaluatorRegistry evaluatorRegistry) {
        this.evaluatorRegistry = evaluatorRegistry;
    }

    public EvaluationOutcome calculate(EvaluationContext context) {
        BigDecimal total = ZERO;
        BigDecimal maximum = ZERO;
        List<QuestionEvaluationOutcome> outcomes = new ArrayList<>(context.questions().size());
        for (QuestionEvaluationContext question : context.questions()) {
            QuestionEvaluationOutcome outcome = evaluatorRegistry.evaluate(question);
            if (outcome.state() == QuestionOutcomeState.INVALID_RESPONSE) {
                throw new InvalidResponseDataException(
                        "Stored response is invalid for question version " + question.questionVersionId());
            }
            total = total.add(orZero(outcome.awardedMarks()));
            maximum = maximum.add(orZero(outcome.maximumMarks()));
            outcomes.add(outcome);
        }
        BigDecimal obtained = RoundingPolicy.marks(total);
        return new EvaluationOutcome(obtained, percentage(obtained, context.publishedTotalMarks()),
                RoundingPolicy.marks(maximum), outcomes);
    }

    /**
     * {@code percentage = obtained_marks / published_total_marks * 100}, HALF_UP to 2 decimals
     * ({@code §34}). The denominator is never inferred from the answered count.
     */
    private BigDecimal percentage(BigDecimal obtained, BigDecimal publishedTotalMarks) {
        if (publishedTotalMarks == null || publishedTotalMarks.signum() == 0) {
            throw new com.company.exam.evaluation.exception.ScoringConfigurationInvalidException(
                    "Published total marks must be present and non-zero to compute a percentage");
        }
        return obtained.multiply(HUNDRED).divide(publishedTotalMarks, RoundingPolicy.PERCENTAGE_SCALE,
                RoundingMode.HALF_UP);
    }

    private BigDecimal orZero(BigDecimal value) {
        return value == null ? ZERO : value;
    }
}
