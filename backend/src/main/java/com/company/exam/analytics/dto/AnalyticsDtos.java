package com.company.exam.analytics.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Analytics read payloads ({@code specs/17-P8-Analytics-V1.5.md §57, §137-§141}).
 *
 * <p>These are read-only projections. Missing data is {@code null}, never a misleading zero
 * ({@code §90}); derived percentages are provided without adding redundant columns ({@code §28}).
 */
public final class AnalyticsDtos {

    private AnalyticsDtos() {
    }

    /** The frozen StudentAnalytics projection ({@code §137}). */
    public record StudentAnalyticsResponse(Integer assessmentsAttempted, BigDecimal averageScore,
            BigDecimal accuracy, BigDecimal averageTime, Instant lastCalculatedOn, String freshness) {
    }

    /** The frozen AssessmentAnalytics projection ({@code §138}). */
    public record AssessmentAnalyticsResponse(UUID assessmentId, Integer totalAttempts, BigDecimal averageScore,
            BigDecimal highestScore, BigDecimal lowestScore, Instant lastCalculatedOn, String freshness) {
    }

    /** The frozen QuestionAnalytics projection plus derived percentages ({@code §28, §139}). */
    public record QuestionAnalyticsResponse(UUID questionId, Integer totalAttempts, Integer correctAttempts,
            Integer incorrectAttempts, BigDecimal averageTime, BigDecimal difficultyIndex,
            BigDecimal correctPercentage, BigDecimal incorrectPercentage, BigDecimal skippedPercentage,
            Instant lastCalculatedOn, String freshness) {
    }

    /** The frozen TopicAnalytics projection plus a sample-size guard ({@code §86, §140}). */
    public record TopicAnalyticsResponse(UUID topicId, Integer totalAttempts, BigDecimal averageScore,
            BigDecimal accuracy, int sampleSize, boolean robustSample, Instant lastCalculatedOn, String freshness) {
    }

    /** The frozen OrganizationAnalytics projection ({@code §141}). */
    public record OrganizationAnalyticsResponse(UUID organizationId, Integer totalStudents, Integer totalAssessments,
            Integer totalAttempts, Integer activeUsers, Instant lastCalculatedOn, String freshness) {
    }

    /** One assessed attempt in the student's assessment read model ({@code §50}). */
    public record StudentAssessmentItem(UUID assessmentId, UUID attemptId, UUID assessmentVersionId,
            String attemptMode, BigDecimal score, BigDecimal percentage, String resultStatus, Instant startedOn) {
    }

    /** One topic in the student's topic read model ({@code §50, §133}). */
    public record StudentTopicItem(UUID topicId, Integer evaluatedOccurrences, BigDecimal accuracy,
            BigDecimal averageScore, boolean robustSample, boolean weakArea) {
    }

    /** Dynamic section read model ({@code §45-§48}). No dedicated table is introduced. */
    public record SectionAnalyticsView(String sectionName, int questionCount, BigDecimal totalMarks,
            BigDecimal obtainedMarks, BigDecimal accuracy, BigDecimal averageTime) {
    }

    /** The student dashboard read model ({@code §50}). */
    public record StudentDashboard(StudentAnalyticsResponse summary, List<StudentAssessmentItem> recentAssessments,
            List<StudentTopicItem> topics) {
    }

    /** The assessment dashboard read model ({@code §53}). */
    public record AssessmentDashboard(AssessmentAnalyticsResponse summary, List<SectionAnalyticsView> sections) {
    }

    /** A data-quality finding ({@code §114}). */
    public record DataQualityFinding(String check, String scope, String detail) {
    }

    /** The data-quality report ({@code §113-§117}). */
    public record DataQualityReport(int findings, List<DataQualityFinding> details) {
    }
}
