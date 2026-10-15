package com.company.exam.analytics.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.exam.analytics.config.AnalyticsProperties;
import com.company.exam.analytics.entity.AssessmentAnalytics;
import com.company.exam.analytics.entity.QuestionAnalytics;
import com.company.exam.analytics.entity.StudentAnalytics;
import com.company.exam.analytics.entity.TopicAnalytics;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * The pure analytics calculators ({@code specs/17-P8-Analytics-V1.5.md §14-§44, §90, §115}).
 */
class AnalyticsCalculatorTest {

    private final AnalyticsCalculator calculator = new AnalyticsCalculator(new AnalyticsProperties());
    private final UUID questionId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID assessmentId = UUID.randomUUID();

    private AnalyticsFact.QuestionFact question(String state, BigDecimal awarded, Integer seconds) {
        return new AnalyticsFact.QuestionFact(UUID.randomUUID(), questionId, UUID.randomUUID(), "General",
                state, awarded, new BigDecimal("2.00"), seconds, true);
    }

    private AnalyticsFact attempt(BigDecimal score, List<AnalyticsFact.QuestionFact> questions) {
        return new AnalyticsFact(UUID.randomUUID(), userId, assessmentId, UUID.randomUUID(), score, questions);
    }

    @Test
    void studentAccuracyExcludesUnansweredAndAveragesScore() {
        List<AnalyticsFact> facts = List.of(
                attempt(new BigDecimal("4.00"), List.of(question("CORRECT", new BigDecimal("2.00"), 10),
                        question("UNANSWERED", null, null))),
                attempt(new BigDecimal("2.00"), List.of(question("INCORRECT", new BigDecimal("-0.50"), 20))));
        StudentAnalytics row = calculator.student(userId, facts, Instant.now());
        assertThat(row.getAssessmentsAttempted()).isEqualTo(2);
        assertThat(row.getAverageScore()).isEqualByComparingTo("3.00");
        // accuracy = 1 correct / (1 correct + 1 incorrect) = 50%
        assertThat(row.getAccuracy()).isEqualByComparingTo("50.00");
        // average time over answered questions only: (10 + 20) / 2
        assertThat(row.getAverageTime()).isEqualByComparingTo("15.00");
    }

    @Test
    void missingPopulationYieldsNullRatherThanZero() {
        StudentAnalytics row = calculator.student(userId, List.of(), Instant.now());
        assertThat(row.getAssessmentsAttempted()).isZero();
        assertThat(row.getAverageScore()).isNull();
        assertThat(row.getAccuracy()).isNull();
        assertThat(row.getAverageTime()).isNull();
    }

    @Test
    void assessmentAggregatesCountAverageHighestAndLowest() {
        List<AnalyticsFact> facts = List.of(attempt(new BigDecimal("4.00"), List.of()),
                attempt(new BigDecimal("-1.00"), List.of()));
        AssessmentAnalytics row = calculator.assessment(assessmentId, facts, Instant.now());
        assertThat(row.getTotalAttempts()).isEqualTo(2);
        assertThat(row.getAverageScore()).isEqualByComparingTo("1.50");
        assertThat(row.getHighestScore()).isEqualByComparingTo("4.00");
        assertThat(row.getLowestScore()).isEqualByComparingTo("-1.00");
    }

    @Test
    void difficultyIndexUsesTheFrozenFormula() {
        QuestionAnalytics row = calculator.question(questionId,
                List.of(question("CORRECT", new BigDecimal("2.00"), 5), question("INCORRECT", BigDecimal.ZERO, 5)),
                Instant.now());
        assertThat(row.getTotalAttempts()).isEqualTo(2);
        assertThat(row.getCorrectAttempts()).isEqualTo(1);
        assertThat(row.getIncorrectAttempts()).isEqualTo(1);
        // 1 - (1 / 2) = 0.5
        assertThat(row.getDifficultyIndex()).isEqualByComparingTo("0.5000");
    }

    @Test
    void difficultyIndexIsNullWhenNothingWasAnswered() {
        QuestionAnalytics row = calculator.question(questionId,
                List.of(question("UNANSWERED", null, null), question("DROPPED", null, null)), Instant.now());
        assertThat(row.getTotalAttempts()).isZero();
        assertThat(row.getDifficultyIndex()).isNull();
    }

    @Test
    void bonusAndDroppedDoNotCountTowardTheDenominator() {
        QuestionAnalytics row = calculator.question(questionId,
                List.of(question("BONUS", new BigDecimal("2.00"), null), question("CORRECT", new BigDecimal("2.00"), 3)),
                Instant.now());
        assertThat(row.getTotalAttempts()).isEqualTo(1);
        assertThat(row.getCorrectAttempts()).isEqualTo(1);
        assertThat(row.getDifficultyIndex()).isEqualByComparingTo("0.0000");
    }

    @Test
    void topicAccuracyAndAverageAwardedMarks() {
        TopicAnalytics row = calculator.topic(UUID.randomUUID(),
                List.of(question("CORRECT", new BigDecimal("2.00"), 4), question("INCORRECT", BigDecimal.ZERO, 6)),
                Instant.now());
        assertThat(row.getTotalAttempts()).isEqualTo(2);
        assertThat(row.getAccuracy()).isEqualByComparingTo("50.00");
        assertThat(row.getAverageScore()).isEqualByComparingTo("1.00");
    }
}
