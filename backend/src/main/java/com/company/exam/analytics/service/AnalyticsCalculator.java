package com.company.exam.analytics.service;

import com.company.exam.analytics.config.AnalyticsProperties;
import com.company.exam.analytics.entity.AssessmentAnalytics;
import com.company.exam.analytics.entity.OrganizationAnalytics;
import com.company.exam.analytics.entity.QuestionAnalytics;
import com.company.exam.analytics.entity.StudentAnalytics;
import com.company.exam.analytics.entity.TopicAnalytics;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * The pure analytics calculators ({@code specs/17-P8-Analytics-V1.5.md §14-§44, §98-§102}).
 *
 * <p>Deterministic functions of the source facts; no database access. Missing data yields
 * {@code null} rather than zero ({@code §90}), accuracy is bounded to [0, 100] ({@code §115}), and
 * the difficulty index uses only the frozen formula ({@code §35}).
 */
@Component
public class AnalyticsCalculator {

    private final AnalyticsProperties properties;

    public AnalyticsCalculator(AnalyticsProperties properties) {
        this.properties = properties;
    }

    /** Student grain ({@code §15-§18}): qualifying attempt count, average score, accuracy, average time. */
    public StudentAnalytics student(UUID userId, List<AnalyticsFact> facts, Instant now) {
        List<BigDecimal> scores = new ArrayList<>();
        long correct = 0;
        long incorrect = 0;
        List<BigDecimal> times = new ArrayList<>();
        int attempts = 0;
        for (AnalyticsFact fact : facts) {
            attempts++;
            if (fact.score() != null) {
                scores.add(fact.score());
            }
            for (AnalyticsFact.QuestionFact question : fact.questions()) {
                if (question.correct()) {
                    correct++;
                } else if (question.counted()) {
                    incorrect++;
                }
                addTime(times, question);
            }
        }
        StudentAnalytics row = new StudentAnalytics();
        row.setUserId(userId);
        row.setAssessmentsAttempted(attempts);
        row.setAverageScore(AnalyticsMath.score(AnalyticsMath.mean(scores)));
        row.setAccuracy(AnalyticsMath.accuracy(correct, incorrect));
        row.setAverageTime(AnalyticsMath.time(AnalyticsMath.mean(times)));
        row.setLastCalculatedOn(now);
        return row;
    }

    /** Assessment grain ({@code §23, §25-§27}): attempt count, average, highest and lowest score. */
    public AssessmentAnalytics assessment(UUID assessmentId, List<AnalyticsFact> facts, Instant now) {
        List<BigDecimal> scores = new ArrayList<>();
        for (AnalyticsFact fact : facts) {
            if (fact.score() != null) {
                scores.add(fact.score());
            }
        }
        AssessmentAnalytics row = new AssessmentAnalytics();
        row.setAssessmentId(assessmentId);
        row.setTotalAttempts(facts.size());
        row.setAverageScore(AnalyticsMath.score(AnalyticsMath.mean(scores)));
        row.setHighestScore(AnalyticsMath.score(scores.stream().max(BigDecimal::compareTo).orElse(null)));
        row.setLowestScore(AnalyticsMath.score(scores.stream().min(BigDecimal::compareTo).orElse(null)));
        row.setLastCalculatedOn(now);
        return row;
    }

    /** Question grain ({@code §28-§35}): counts, average time and the frozen difficulty index. */
    public QuestionAnalytics question(UUID questionId, List<AnalyticsFact.QuestionFact> facts, Instant now) {
        long correct = 0;
        long incorrect = 0;
        List<BigDecimal> times = new ArrayList<>();
        for (AnalyticsFact.QuestionFact question : facts) {
            if (question.correct()) {
                correct++;
            } else if (question.counted()) {
                incorrect++;
            }
            addTime(times, question);
        }
        long total = correct + incorrect;
        QuestionAnalytics row = new QuestionAnalytics();
        row.setQuestionId(questionId);
        row.setTotalAttempts((int) total);
        row.setCorrectAttempts((int) correct);
        row.setIncorrectAttempts((int) incorrect);
        row.setAverageTime(AnalyticsMath.time(AnalyticsMath.mean(times)));
        row.setDifficultyIndex(AnalyticsMath.difficultyIndex(correct, total));
        row.setLastCalculatedOn(now);
        return row;
    }

    /** Topic grain ({@code §36-§39}): evaluated occurrences, accuracy, and average awarded marks. */
    public TopicAnalytics topic(UUID topicId, List<AnalyticsFact.QuestionFact> facts, Instant now) {
        long correct = 0;
        long incorrect = 0;
        List<BigDecimal> awarded = new ArrayList<>();
        for (AnalyticsFact.QuestionFact question : facts) {
            if (question.correct()) {
                correct++;
            } else if (question.counted()) {
                incorrect++;
            }
            if (question.counted() && question.awardedMarks() != null) {
                awarded.add(question.awardedMarks());
            }
        }
        TopicAnalytics row = new TopicAnalytics();
        row.setTopicId(topicId);
        row.setTotalAttempts((int) (correct + incorrect));
        row.setAverageScore(AnalyticsMath.score(AnalyticsMath.mean(awarded)));
        row.setAccuracy(AnalyticsMath.accuracy(correct, incorrect));
        row.setLastCalculatedOn(now);
        return row;
    }

    /** Organization grain ({@code §41-§44}). */
    public OrganizationAnalytics organization(UUID organizationId, int totalStudents, int totalAssessments,
            int totalAttempts, int activeUsers, Instant now) {
        OrganizationAnalytics row = new OrganizationAnalytics();
        row.setOrganizationId(organizationId);
        row.setTotalStudents(totalStudents);
        row.setTotalAssessments(totalAssessments);
        row.setTotalAttempts(totalAttempts);
        row.setActiveUsers(activeUsers);
        row.setLastCalculatedOn(now);
        return row;
    }

    /** Answered question time, excluding impossible outliers ({@code §18, §116}). */
    private void addTime(List<BigDecimal> times, AnalyticsFact.QuestionFact question) {
        Integer seconds = question.timeSpentSeconds();
        if (seconds == null || seconds < 0 || seconds > properties.getAverageTimeUpperBoundSeconds()) {
            return;
        }
        times.add(BigDecimal.valueOf(seconds));
    }
}
