package com.company.exam.reporting.projection;

import static com.company.exam.reporting.projection.Projections.MAX_ROWS;
import static com.company.exam.reporting.projection.Projections.appliedFilters;
import static com.company.exam.reporting.projection.Projections.columns;
import static com.company.exam.reporting.projection.Projections.decimal;
import static com.company.exam.reporting.projection.Projections.period;
import static com.company.exam.reporting.projection.Projections.row;
import static com.company.exam.reporting.projection.Projections.text;

import com.company.exam.analytics.api.ReportingAnalyticsCatalog;
import com.company.exam.assessment.api.AssessmentDefinitionCatalog;
import com.company.exam.assessment.api.ReportingAssessmentCatalog;
import com.company.exam.content.api.QuestionTaxonomyCatalog;
import com.company.exam.delivery.api.ReportingDeliveryCatalog;
import com.company.exam.evaluation.api.AnalyticsOutcomeCatalog;
import com.company.exam.reporting.catalog.ReportType;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Assessment report projections ({@code specs/22-P13-Reporting-V1.5.md §42-§47}).
 *
 * <p>Assessment identity and published versions come from Assessment, attempts from Delivery,
 * correctness from the authoritative Evaluation outcomes, and aggregate measures from the frozen
 * {@code assessment_analytics} and {@code question_analytics} aggregates ({@code §43, §46, §88}).
 * Nothing is published, scored or mutated here ({@code §7.3}).
 */
public final class AssessmentReportProviders {

    private AssessmentReportProviders() {
    }

    /** ASSESSMENT_SUMMARY — assessment identity, status, version and question count (§43). */
    @Component
    public static class AssessmentSummaryProvider implements ReportDataProvider {

        private final ReportingAssessmentCatalog assessmentCatalog;

        public AssessmentSummaryProvider(ReportingAssessmentCatalog assessmentCatalog) {
            this.assessmentCatalog = assessmentCatalog;
        }

        @Override
        public ReportType reportType() {
            return ReportType.ASSESSMENT_SUMMARY;
        }

        @Override
        public ReportProjection project(ReportCriteria criteria) {
            List<ReportingAssessmentCatalog.AssessmentRow> assessments = assessmentCatalog.assessments(MAX_ROWS);
            if (assessments.isEmpty()) {
                return Projections.empty(ReportType.ASSESSMENT_SUMMARY.displayName(), criteria);
            }
            List<UUID> assessmentIds = assessments.stream()
                    .map(ReportingAssessmentCatalog.AssessmentRow::assessmentId).toList();
            Map<UUID, List<ReportingAssessmentCatalog.VersionRow>> versionsByAssessment = new HashMap<>();
            for (ReportingAssessmentCatalog.VersionRow version : assessmentCatalog.versionsOf(assessmentIds)) {
                versionsByAssessment.computeIfAbsent(version.assessmentId(), ignored -> new ArrayList<>())
                        .add(version);
            }

            List<Map<String, String>> rows = new ArrayList<>();
            for (ReportingAssessmentCatalog.AssessmentRow assessment : assessments) {
                List<ReportingAssessmentCatalog.VersionRow> versions =
                        versionsByAssessment.getOrDefault(assessment.assessmentId(), List.of());
                ReportingAssessmentCatalog.VersionRow latest = versions.stream()
                        .max(java.util.Comparator.comparing(version -> version.version() == null
                                ? 0 : version.version()))
                        .orElse(null);
                rows.add(row(
                        "assessmentId", text(assessment.assessmentId()),
                        "code", assessment.code(),
                        "name", assessment.name(),
                        "status", assessment.status(),
                        "currentVersion", latest == null ? "" : latest.version(),
                        "published", latest != null && latest.published(),
                        "totalQuestions", latest == null ? "" : latest.totalQuestions(),
                        "durationMinutes", latest == null ? "" : latest.durationMinutes()));
            }
            return new ReportProjection(ReportType.ASSESSMENT_SUMMARY.displayName(), "Current",
                    appliedFilters(criteria.filters()), criteria.generatedAt(), criteria.callerId(),
                    columns("assessmentId", "Assessment id", "code", "Code", "name", "Name",
                            "status", "Status", "currentVersion", "Latest version", "published", "Published",
                            "totalQuestions", "Questions", "durationMinutes", "Duration (minutes)"),
                    rows, List.of(), "Assessment identity and its latest version; no attempt data is included.");
        }
    }

    /**
     * ASSESSMENT_ATTEMPTS — attempts recorded against assessment versions in a period (§44).
     *
     * <p>This is the {@code ASYNC_PREFERRED} report in the catalog, so a large period is queued rather
     * than executed inline ({@code §24, §93}). The optional {@code assessmentId} filter is expanded to
     * that assessment's versions before the attempt read, so the query stays bounded.
     */
    @Component
    public static class AssessmentAttemptsProvider implements ReportDataProvider {

        private final ReportingAssessmentCatalog assessmentCatalog;
        private final ReportingDeliveryCatalog deliveryCatalog;

        public AssessmentAttemptsProvider(ReportingAssessmentCatalog assessmentCatalog,
                ReportingDeliveryCatalog deliveryCatalog) {
            this.assessmentCatalog = assessmentCatalog;
            this.deliveryCatalog = deliveryCatalog;
        }

        @Override
        public ReportType reportType() {
            return ReportType.ASSESSMENT_ATTEMPTS;
        }

        @Override
        public ReportProjection project(ReportCriteria criteria) {
            List<UUID> versionIds = resolveVersionIds(criteria);
            if (versionIds.isEmpty()) {
                return Projections.empty(ReportType.ASSESSMENT_ATTEMPTS.displayName(), criteria);
            }
            List<ReportingDeliveryCatalog.AttemptRow> attempts = deliveryCatalog.attemptsForVersions(versionIds,
                    criteria.instant("from"), criteria.instant("to"), MAX_ROWS);
            if (attempts.isEmpty()) {
                return Projections.empty(ReportType.ASSESSMENT_ATTEMPTS.displayName(), criteria);
            }
            List<Map<String, String>> rows = new ArrayList<>();
            for (ReportingDeliveryCatalog.AttemptRow attempt : attempts) {
                rows.add(row(
                        "attemptId", text(attempt.attemptId()),
                        "assessmentVersionId", text(attempt.assessmentVersionId()),
                        "userId", text(attempt.userId()),
                        "mode", attempt.mode(),
                        "status", attempt.status(),
                        "startedOn", text(attempt.startedOn()),
                        "submittedOn", text(attempt.submittedOn()),
                        "totalScore", decimal(attempt.totalScore())));
            }
            return new ReportProjection(ReportType.ASSESSMENT_ATTEMPTS.displayName(), period(criteria.filters()),
                    appliedFilters(criteria.filters()), criteria.generatedAt(), criteria.callerId(),
                    columns("attemptId", "Attempt id", "assessmentVersionId", "Assessment version",
                            "userId", "Student", "mode", "Mode", "status", "State", "startedOn", "Started",
                            "submittedOn", "Submitted", "totalScore", "Total score"),
                    rows, List.of(), "Attempt state per assessment version; no answer-key detail is included.");
        }

        /** The assessment versions in scope: one assessment if filtered, else every version. */
        private List<UUID> resolveVersionIds(ReportCriteria criteria) {
            UUID assessmentId = criteria.uuid("assessmentId");
            List<ReportingAssessmentCatalog.AssessmentRow> assessments = assessmentCatalog.assessments(MAX_ROWS);
            List<UUID> assessmentIds = assessmentId != null ? List.of(assessmentId)
                    : assessments.stream().map(ReportingAssessmentCatalog.AssessmentRow::assessmentId).toList();
            List<UUID> versionIds = new ArrayList<>();
            for (ReportingAssessmentCatalog.VersionRow version : assessmentCatalog.versionsOf(assessmentIds)) {
                versionIds.add(version.versionId());
            }
            return versionIds;
        }
    }

    /** ASSESSMENT_PERFORMANCE — attempt count, score summary and pass rate per assessment (§45). */
    @Component
    public static class AssessmentPerformanceProvider implements ReportDataProvider {

        private final ReportingAssessmentCatalog assessmentCatalog;
        private final ReportingAnalyticsCatalog analyticsCatalog;
        private final ReportingDeliveryCatalog deliveryCatalog;

        public AssessmentPerformanceProvider(ReportingAssessmentCatalog assessmentCatalog,
                ReportingAnalyticsCatalog analyticsCatalog, ReportingDeliveryCatalog deliveryCatalog) {
            this.assessmentCatalog = assessmentCatalog;
            this.analyticsCatalog = analyticsCatalog;
            this.deliveryCatalog = deliveryCatalog;
        }

        @Override
        public ReportType reportType() {
            return ReportType.ASSESSMENT_PERFORMANCE;
        }

        @Override
        public ReportProjection project(ReportCriteria criteria) {
            List<ReportingAssessmentCatalog.AssessmentRow> assessments = assessmentCatalog.assessments(MAX_ROWS);
            if (assessments.isEmpty()) {
                return Projections.empty(ReportType.ASSESSMENT_PERFORMANCE.displayName(), criteria);
            }
            List<UUID> assessmentIds = assessments.stream()
                    .map(ReportingAssessmentCatalog.AssessmentRow::assessmentId).toList();
            Map<UUID, ReportingAnalyticsCatalog.AssessmentAnalyticsRow> byAssessment = new HashMap<>();
            for (ReportingAnalyticsCatalog.AssessmentAnalyticsRow row : analyticsCatalog.assessments(assessmentIds)) {
                byAssessment.put(row.assessmentId(), row);
            }

            List<Map<String, String>> rows = new ArrayList<>();
            List<ReportProjection.SummaryMetric> metrics = new ArrayList<>();
            for (ReportingAssessmentCatalog.AssessmentRow assessment : assessments) {
                ReportingAnalyticsCatalog.AssessmentAnalyticsRow analytics = byAssessment.get(
                        assessment.assessmentId());
                if (analytics == null) {
                    // §45: a distribution is not fabricated when the source is not calculated yet.
                    continue;
                }
                rows.add(row(
                        "assessmentId", text(assessment.assessmentId()),
                        "code", assessment.code(),
                        "name", assessment.name(),
                        "totalAttempts", analytics.totalAttempts(),
                        "averageScore", decimal(analytics.averageScore()),
                        "highestScore", decimal(analytics.highestScore()),
                        "lowestScore", decimal(analytics.lowestScore()),
                        "lastCalculatedOn", text(analytics.lastCalculatedOn())));
                if (analytics.averageScore() != null) {
                    metrics.add(new ReportProjection.SummaryMetric(
                            "Average score · " + assessment.code(),
                            decimal(analytics.averageScore()),
                            "Mean obtained marks across published results (assessment_analytics.average_score)"));
                }
            }
            if (rows.isEmpty()) {
                return Projections.empty(ReportType.ASSESSMENT_PERFORMANCE.displayName(), criteria);
            }
            return new ReportProjection(ReportType.ASSESSMENT_PERFORMANCE.displayName(), "Current",
                    appliedFilters(criteria.filters()), criteria.generatedAt(), criteria.callerId(),
                    columns("assessmentId", "Assessment id", "code", "Code", "name", "Name",
                            "totalAttempts", "Attempts", "averageScore", "Average score",
                            "highestScore", "Highest", "lowestScore", "Lowest", "lastCalculatedOn",
                            "Last calculated"),
                    rows, metrics,
                    "Sourced from the frozen assessment_analytics aggregate; assessments with no calculated "
                            + "aggregate are omitted rather than reported as zero (§45, §91).");
        }
    }

    /** ASSESSMENT_QUESTION_ANALYSIS — per-question difficulty from question_analytics (§46). */
    @Component
    public static class AssessmentQuestionAnalysisProvider implements ReportDataProvider {

        private final ReportingAssessmentCatalog assessmentCatalog;
        private final AssessmentDefinitionCatalog definitionCatalog;
        private final QuestionTaxonomyCatalog taxonomyCatalog;
        private final AnalyticsOutcomeCatalog outcomeCatalog;
        private final ReportingAnalyticsCatalog analyticsCatalog;

        public AssessmentQuestionAnalysisProvider(ReportingAssessmentCatalog assessmentCatalog,
                AssessmentDefinitionCatalog definitionCatalog, QuestionTaxonomyCatalog taxonomyCatalog,
                AnalyticsOutcomeCatalog outcomeCatalog, ReportingAnalyticsCatalog analyticsCatalog) {
            this.assessmentCatalog = assessmentCatalog;
            this.definitionCatalog = definitionCatalog;
            this.taxonomyCatalog = taxonomyCatalog;
            this.outcomeCatalog = outcomeCatalog;
            this.analyticsCatalog = analyticsCatalog;
        }

        @Override
        public ReportType reportType() {
            return ReportType.ASSESSMENT_QUESTION_ANALYSIS;
        }

        @Override
        public ReportProjection project(ReportCriteria criteria) {
            UUID assessmentId = criteria.uuid("assessmentId");
            List<UUID> versionIds = new ArrayList<>();
            if (assessmentId != null) {
                for (ReportingAssessmentCatalog.VersionRow version
                        : assessmentCatalog.versionsOf(List.of(assessmentId))) {
                    versionIds.add(version.versionId());
                }
            } else {
                for (ReportingAssessmentCatalog.VersionRow version
                        : assessmentCatalog.versionsOf(assessmentCatalog.assessments(MAX_ROWS).stream()
                                .map(ReportingAssessmentCatalog.AssessmentRow::assessmentId).toList())) {
                    versionIds.add(version.versionId());
                }
            }
            if (versionIds.isEmpty()) {
                return Projections.empty(ReportType.ASSESSMENT_QUESTION_ANALYSIS.displayName(), criteria);
            }

            // The question versions the assessment actually binds, in display order.
            Map<UUID, String> sectionByVersion = new LinkedHashMap<>();
            for (UUID versionId : versionIds) {
                for (AssessmentDefinitionCatalog.AssessmentQuestionView question : definitionCatalog
                        .questions(versionId)) {
                    sectionByVersion.putIfAbsent(question.questionVersionId(), question.sectionName());
                }
            }
            if (sectionByVersion.isEmpty()) {
                return Projections.empty(ReportType.ASSESSMENT_QUESTION_ANALYSIS.displayName(), criteria);
            }

            Map<UUID, UUID> questionByVersion = taxonomyCatalog.questionIdByVersionId(sectionByVersion.keySet());
            Map<UUID, ReportingAnalyticsCatalog.QuestionAnalyticsRow> analyticsByQuestion = new HashMap<>();
            for (ReportingAnalyticsCatalog.QuestionAnalyticsRow row
                    : analyticsCatalog.questions(questionByVersion.values())) {
                analyticsByQuestion.put(row.questionId(), row);
            }

            List<Map<String, String>> rows = new ArrayList<>();
            for (Map.Entry<UUID, String> entry : sectionByVersion.entrySet()) {
                UUID questionId = questionByVersion.get(entry.getKey());
                ReportingAnalyticsCatalog.QuestionAnalyticsRow analytics = questionId == null ? null
                        : analyticsByQuestion.get(questionId);
                rows.add(row(
                        "questionVersionId", text(entry.getKey()),
                        "questionId", text(questionId),
                        "section", entry.getValue(),
                        "totalAttempts", analytics == null ? "" : analytics.totalAttempts(),
                        "correctAttempts", analytics == null ? "" : analytics.correctAttempts(),
                        "incorrectAttempts", analytics == null ? "" : analytics.incorrectAttempts(),
                        "averageTime", analytics == null ? "" : decimal(analytics.averageTime()),
                        "difficultyIndex", analytics == null ? "" : decimal(analytics.difficultyIndex())));
            }
            return new ReportProjection(ReportType.ASSESSMENT_QUESTION_ANALYSIS.displayName(), "Current",
                    appliedFilters(criteria.filters()), criteria.generatedAt(), criteria.callerId(),
                    columns("questionVersionId", "Question version", "questionId", "Question", "section", "Section",
                            "totalAttempts", "Attempts", "correctAttempts", "Correct",
                            "incorrectAttempts", "Incorrect", "averageTime", "Average time",
                            "difficultyIndex", "Difficulty index"),
                    rows, List.of(),
                    "Question measures come from the frozen question_analytics aggregate; a question with no "
                            + "calculated aggregate shows blank measures rather than a fabricated zero (§46).");
        }
    }

    /** ASSESSMENT_SCHEDULE_ACTIVITY — schedule windows and their attempt counts (§47). */
    @Component
    public static class AssessmentScheduleActivityProvider implements ReportDataProvider {

        private final ReportingAssessmentCatalog assessmentCatalog;
        private final ReportingDeliveryCatalog deliveryCatalog;

        public AssessmentScheduleActivityProvider(ReportingAssessmentCatalog assessmentCatalog,
                ReportingDeliveryCatalog deliveryCatalog) {
            this.assessmentCatalog = assessmentCatalog;
            this.deliveryCatalog = deliveryCatalog;
        }

        @Override
        public ReportType reportType() {
            return ReportType.ASSESSMENT_SCHEDULE_ACTIVITY;
        }

        @Override
        public ReportProjection project(ReportCriteria criteria) {
            UUID assessmentId = criteria.uuid("assessmentId");
            List<UUID> assessmentIds = assessmentId != null ? List.of(assessmentId)
                    : assessmentCatalog.assessments(MAX_ROWS).stream()
                            .map(ReportingAssessmentCatalog.AssessmentRow::assessmentId).toList();
            List<UUID> versionIds = assessmentCatalog.versionsOf(assessmentIds).stream()
                    .map(ReportingAssessmentCatalog.VersionRow::versionId).toList();
            List<ReportingAssessmentCatalog.ScheduleRow> schedules = assessmentCatalog
                    .schedulesForVersions(versionIds);
            if (schedules.isEmpty()) {
                return Projections.empty(ReportType.ASSESSMENT_SCHEDULE_ACTIVITY.displayName(), criteria);
            }
            List<Map<String, String>> rows = new ArrayList<>();
            for (ReportingAssessmentCatalog.ScheduleRow schedule : schedules) {
                // The attempt count is linked through the approved Delivery relationship (§47), not
                // inferred from timestamps.
                long attempts = deliveryCatalog.attemptsForSchedule(schedule.scheduleId(), MAX_ROWS).size();
                rows.add(row(
                        "scheduleId", text(schedule.scheduleId()),
                        "assessmentVersionId", text(schedule.assessmentVersionId()),
                        "mode", schedule.mode(),
                        "startTime", text(schedule.startTime()),
                        "endTime", text(schedule.endTime()),
                        "status", schedule.status(),
                        "attemptCount", attempts));
            }
            return new ReportProjection(ReportType.ASSESSMENT_SCHEDULE_ACTIVITY.displayName(), "Current",
                    appliedFilters(criteria.filters()), criteria.generatedAt(), criteria.callerId(),
                    columns("scheduleId", "Schedule id", "assessmentVersionId", "Assessment version",
                            "mode", "Mode", "startTime", "Start", "endTime", "End", "status", "Status",
                            "attemptCount", "Attempts"),
                    rows, List.of(),
                    "Schedule identity and its linked attempt count; attendance meaning is not inferred from "
                            + "raw timestamps (§47).");
        }
    }
}
