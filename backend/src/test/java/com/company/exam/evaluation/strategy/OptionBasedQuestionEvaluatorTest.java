package com.company.exam.evaluation.strategy;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.exam.evaluation.entity.QuestionOutcome;
import com.company.exam.evaluation.scoring.QuestionEvaluationContext;
import com.company.exam.evaluation.scoring.QuestionEvaluationOutcome;
import com.company.exam.evaluation.scoring.QuestionOutcomeState;
import com.company.exam.evaluation.scoring.QuestionScoringPolicy;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Option-based objective scoring ({@code specs/15-P6-Evaluation-V1.5.md §13-§21, §134}).
 */
class OptionBasedQuestionEvaluatorTest {

    private final OptionBasedQuestionEvaluator evaluator =
            new OptionBasedQuestionEvaluator(new QuestionScoringPolicy());

    private final UUID questionVersionId = UUID.randomUUID();
    private final UUID optionA = UUID.randomUUID();
    private final UUID optionB = UUID.randomUUID();

    private QuestionEvaluationContext context(String response, BigDecimal marks, BigDecimal negative,
            QuestionOutcome outcome, BigDecimal overrideMarks, BigDecimal overrideNegative) {
        return new QuestionEvaluationContext(questionVersionId, "SINGLE_CHOICE", marks, negative, response, outcome,
                optionA, null, overrideMarks, overrideNegative, Map.of(optionA, "A", optionB, "B"));
    }

    @Test
    void correctOptionKeyAwardsFullMarks() {
        QuestionEvaluationOutcome outcome = evaluator.evaluate(
                context("A", new BigDecimal("2.00"), new BigDecimal("0.50"), QuestionOutcome.NORMAL, null, null));
        assertThat(outcome.state()).isEqualTo(QuestionOutcomeState.CORRECT);
        assertThat(outcome.awardedMarks()).isEqualByComparingTo("2.00");
        assertThat(outcome.negativeMarksApplied()).isEqualByComparingTo("0");
    }

    @Test
    void correctOptionIdIsAcceptedAsAnEquivalentIdentity() {
        QuestionEvaluationOutcome outcome = evaluator.evaluate(context(optionA.toString(), new BigDecimal("4.00"),
                BigDecimal.ZERO, QuestionOutcome.NORMAL, null, null));
        assertThat(outcome.state()).isEqualTo(QuestionOutcomeState.CORRECT);
    }

    @Test
    void incorrectOptionAppliesTheNegativeMarks() {
        QuestionEvaluationOutcome outcome = evaluator.evaluate(
                context("B", new BigDecimal("2.00"), new BigDecimal("0.50"), QuestionOutcome.NORMAL, null, null));
        assertThat(outcome.state()).isEqualTo(QuestionOutcomeState.INCORRECT);
        assertThat(outcome.awardedMarks()).isEqualByComparingTo("-0.50");
        assertThat(outcome.negativeMarksApplied()).isEqualByComparingTo("0.50");
    }

    @Test
    void unansweredQuestionScoresZero() {
        QuestionEvaluationOutcome outcome = evaluator.evaluate(
                context(null, new BigDecimal("2.00"), new BigDecimal("0.50"), QuestionOutcome.NORMAL, null, null));
        assertThat(outcome.state()).isEqualTo(QuestionOutcomeState.UNANSWERED);
        assertThat(outcome.awardedMarks()).isEqualByComparingTo("0");
    }

    @Test
    void unknownOptionIdentityIsAnExplicitInvalidResponse() {
        QuestionEvaluationOutcome outcome = evaluator.evaluate(
                context("Z", new BigDecimal("2.00"), BigDecimal.ZERO, QuestionOutcome.NORMAL, null, null));
        assertThat(outcome.state()).isEqualTo(QuestionOutcomeState.INVALID_RESPONSE);
    }

    @Test
    void bonusAwardsTheConfiguredMarksRegardlessOfResponse() {
        QuestionEvaluationOutcome outcome = evaluator.evaluate(
                context("B", new BigDecimal("2.00"), new BigDecimal("0.50"), QuestionOutcome.BONUS, null, null));
        assertThat(outcome.state()).isEqualTo(QuestionOutcomeState.BONUS);
        assertThat(outcome.awardedMarks()).isEqualByComparingTo("2.00");
    }

    @Test
    void droppedContributesZeroAndIgnoresTheResponse() {
        QuestionEvaluationOutcome outcome = evaluator.evaluate(
                context("A", new BigDecimal("2.00"), new BigDecimal("0.50"), QuestionOutcome.DROPPED, null, null));
        assertThat(outcome.state()).isEqualTo(QuestionOutcomeState.DROPPED);
        assertThat(outcome.awardedMarks()).isEqualByComparingTo("0");
    }

    @Test
    void overrideMarksAndNegativeMarksSupersedeTheSnapshotValues() {
        QuestionEvaluationOutcome correct = evaluator.evaluate(context("A", new BigDecimal("2.00"),
                new BigDecimal("0.50"), QuestionOutcome.NORMAL, new BigDecimal("5.00"), null));
        assertThat(correct.awardedMarks()).isEqualByComparingTo("5.00");

        QuestionEvaluationOutcome incorrect = evaluator.evaluate(context("B", new BigDecimal("2.00"),
                new BigDecimal("0.50"), QuestionOutcome.NORMAL, null, new BigDecimal("0.25")));
        assertThat(incorrect.awardedMarks()).isEqualByComparingTo("-0.25");
    }

    @Test
    void zeroNegativeMarksMeansAnIncorrectAnswerCostsNothing() {
        QuestionEvaluationOutcome outcome = evaluator.evaluate(
                context("B", new BigDecimal("2.00"), BigDecimal.ZERO, QuestionOutcome.NORMAL, null, null));
        assertThat(outcome.awardedMarks()).isEqualByComparingTo("0");
    }
}
