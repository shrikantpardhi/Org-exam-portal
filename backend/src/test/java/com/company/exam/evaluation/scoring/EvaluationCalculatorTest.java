package com.company.exam.evaluation.scoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.company.exam.evaluation.entity.QuestionOutcome;
import com.company.exam.evaluation.exception.InvalidResponseDataException;
import com.company.exam.evaluation.exception.ScoringConfigurationInvalidException;
import com.company.exam.evaluation.strategy.NumericAnswerEvaluator;
import com.company.exam.evaluation.strategy.OptionBasedQuestionEvaluator;
import com.company.exam.evaluation.strategy.QuestionEvaluatorRegistry;
import com.company.exam.evaluation.strategy.TextAnswerEvaluator;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Aggregate scoring, rounding, and determinism ({@code specs/15-P6-Evaluation-V1.5.md §31, §32, §34, §74, §133}).
 */
class EvaluationCalculatorTest {

    private final QuestionScoringPolicy policy = new QuestionScoringPolicy();
    private final EvaluationCalculator calculator = new EvaluationCalculator(new QuestionEvaluatorRegistry(
            List.of(new OptionBasedQuestionEvaluator(policy), new NumericAnswerEvaluator(policy),
                    new TextAnswerEvaluator(policy))));

    private final UUID optionA = UUID.randomUUID();
    private final UUID optionB = UUID.randomUUID();

    private QuestionEvaluationContext option(String response, BigDecimal marks, BigDecimal negative) {
        return new QuestionEvaluationContext(UUID.randomUUID(), "SINGLE_CHOICE", marks, negative, response,
                QuestionOutcome.NORMAL, optionA, List.<UUID>of(), null, null, null, Map.of(optionA, "A", optionB, "B"));
    }

    private EvaluationOutcome calculate(BigDecimal totalMarks, List<QuestionEvaluationContext> questions) {
        return calculator.calculate(new EvaluationContext(UUID.randomUUID(), UUID.randomUUID(), totalMarks,
                questions));
    }

    @Test
    void aggregatesMarksAndRoundsHalfUpToTwoDecimals() {
        // 1/3 + 1/3 + 1/3 = 1.00; use thirds to force rounding behaviour.
        EvaluationOutcome outcome = calculate(new BigDecimal("4.00"), List.of(
                option("A", new BigDecimal("1.005"), BigDecimal.ZERO),
                option("A", new BigDecimal("1.005"), BigDecimal.ZERO)));
        assertThat(outcome.obtainedMarks()).isEqualByComparingTo("2.01");
    }

    @Test
    void percentageIsHalfUpToTwoDecimals() {
        EvaluationOutcome outcome = calculate(new BigDecimal("6.00"), List.of(
                option("A", new BigDecimal("2.00"), BigDecimal.ZERO),
                option("B", new BigDecimal("2.00"), new BigDecimal("0.50")),
                option(null, new BigDecimal("2.00"), new BigDecimal("0.50"))));
        assertThat(outcome.obtainedMarks()).isEqualByComparingTo("1.50");
        assertThat(outcome.percentage()).isEqualByComparingTo("25.00");
    }

    @Test
    void theTotalIsNotClampedAtZero() {
        EvaluationOutcome outcome = calculate(new BigDecimal("10.00"), List.of(
                option("B", new BigDecimal("2.00"), new BigDecimal("2.00")),
                option("B", new BigDecimal("2.00"), new BigDecimal("2.00")),
                option("B", new BigDecimal("2.00"), new BigDecimal("2.00"))));
        assertThat(outcome.obtainedMarks()).isEqualByComparingTo("-6.00");
        assertThat(outcome.percentage()).isEqualByComparingTo("-60.00");
    }

    @Test
    void identicalInputsProduceIdenticalOutput() {
        List<QuestionEvaluationContext> questions = List.of(
                option("A", new BigDecimal("2.00"), new BigDecimal("0.50")),
                option("B", new BigDecimal("2.00"), new BigDecimal("0.50")));
        EvaluationOutcome first = calculate(new BigDecimal("4.00"), questions);
        EvaluationOutcome second = calculate(new BigDecimal("4.00"), questions);
        assertThat(second.obtainedMarks()).isEqualByComparingTo(first.obtainedMarks());
        assertThat(second.percentage()).isEqualByComparingTo(first.percentage());
        assertThat(second.maximumMarks()).isEqualByComparingTo(first.maximumMarks());
    }

    @Test
    void aZeroPublishedTotalIsAnInvalidScoringConfiguration() {
        assertThatThrownBy(() -> calculate(BigDecimal.ZERO, List.of(option("A", new BigDecimal("2.00"),
                BigDecimal.ZERO))))
                .isInstanceOf(ScoringConfigurationInvalidException.class);
    }

    @Test
    void aStoredInvalidResponseFailsTheEvaluationRatherThanBeingIgnored() {
        assertThatThrownBy(() -> calculate(new BigDecimal("2.00"), List.of(option("Z", new BigDecimal("2.00"),
                BigDecimal.ZERO))))
                .isInstanceOf(InvalidResponseDataException.class);
    }
}
