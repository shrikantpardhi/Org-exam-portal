package com.company.exam.analytics.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The Analytics read contract consumed by Reporting
 * ({@code specs/22-P13-Reporting-V1.5.md §10, §40, §41, §43, §46, §88}).
 *
 * <p>Reporting reads the frozen analytics aggregates through this contract and never through the
 * analytics repositories directly, so reporting stays decoupled from the persistence layout (§10).
 *
 * <p>{@code §88} makes analytics the reuse source where its grain matches the report: the student and
 * topic reports use the derived aggregates rather than recomputing from transactional tables. Every
 * method is read-only ({@code §7.3}) and the collection methods accept an explicit id set so a report
 * can never scan an unbounded table ({@code §7.6}).
 */
public interface ReportingAnalyticsCatalog {

    /** The frozen {@code student_analytics} aggregate (§40). */
    record StudentAnalyticsRow(UUID userId, Integer assessmentsAttempted, BigDecimal averageScore,
            BigDecimal accuracy, BigDecimal averageTime, Instant lastCalculatedOn) {
    }

    /** The frozen {@code topic_analytics} aggregate (§41). */
    record TopicAnalyticsRow(UUID topicId, Integer totalAttempts, BigDecimal averageScore,
            BigDecimal accuracy, Instant lastCalculatedOn) {
    }

    /** The frozen {@code question_analytics} aggregate (§46). */
    record QuestionAnalyticsRow(UUID questionId, Integer totalAttempts, Integer correctAttempts,
            Integer incorrectAttempts, BigDecimal averageTime, BigDecimal difficultyIndex,
            Instant lastCalculatedOn) {
    }

    /** The frozen {@code assessment_analytics} aggregate; its four measures align with §43. */
    record AssessmentAnalyticsRow(UUID assessmentId, Integer totalAttempts, BigDecimal averageScore,
            BigDecimal highestScore, BigDecimal lowestScore, Instant lastCalculatedOn) {
    }

    /** The frozen {@code organization_analytics} aggregate; the executive scope (§64, §73). */
    record OrganizationAnalyticsRow(UUID organizationId, Integer totalStudents, Integer totalAssessments,
            Integer totalAttempts, Integer activeUsers, Instant lastCalculatedOn) {
    }

    /** The student aggregate, or empty when analytics has never been calculated for that student. */
    Optional<StudentAnalyticsRow> student(UUID userId);

    /** The topic aggregates for the given topics; unknown topics are simply absent. */
    List<TopicAnalyticsRow> topics(Collection<UUID> topicIds);

    /** The question aggregates for the given questions. */
    List<QuestionAnalyticsRow> questions(Collection<UUID> questionIds);

    /** The assessment aggregates for the given logical assessments. */
    List<AssessmentAnalyticsRow> assessments(Collection<UUID> assessmentIds);

    /** The organization aggregates for the given organizations, in the caller's scope (§64). */
    List<OrganizationAnalyticsRow> organizations(Collection<UUID> organizationIds);
}
