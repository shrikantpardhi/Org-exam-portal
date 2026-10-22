package com.company.exam.reporting.projection;

import static com.company.exam.reporting.projection.Projections.MAX_ROWS;
import static com.company.exam.reporting.projection.Projections.appliedFilters;
import static com.company.exam.reporting.projection.Projections.columns;
import static com.company.exam.reporting.projection.Projections.decimal;
import static com.company.exam.reporting.projection.Projections.period;
import static com.company.exam.reporting.projection.Projections.row;
import static com.company.exam.reporting.projection.Projections.text;

import com.company.exam.analytics.api.ReportingAnalyticsCatalog;
import com.company.exam.content.api.QuestionTaxonomyCatalog;
import com.company.exam.delivery.api.AttemptAnalyticsCatalog;
import com.company.exam.delivery.api.ReportingDeliveryCatalog;
import com.company.exam.evaluation.api.AnalyticsOutcomeCatalog;
import com.company.exam.identity.api.AdminUserDirectory;
import com.company.exam.reporting.catalog.ReportType;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Student report projections ({@code specs/22-P13-Reporting-V1.5.md §37-§41}).
 *
 * <p>Every provider scopes to the caller through {@link ReportCriteria#scopedSubject(String)}, so a
 * student can only ever read their own data and an authorized staff caller may name another student
 * ({@code §76, §81}). Identity data is limited to the safe administrative projection — never a
 * password hash, session secret or permission internal ({@code §38, §82}).
 */
public final class StudentReportProviders {

    private StudentReportProviders() {
    }

    /** STUDENT_PROFILE — identity and account status only (§38). */
    @Component
    public static class StudentProfileProvider implements ReportDataProvider {

        private final AdminUserDirectory userDirectory;

        public StudentProfileProvider(AdminUserDirectory userDirectory) {
            this.userDirectory = userDirectory;
        }

        @Override
        public ReportType reportType() {
            return ReportType.STUDENT_PROFILE;
        }

        @Override
        public ReportProjection project(ReportCriteria criteria) {
            UUID studentId = criteria.scopedSubject("studentId");
            var user = studentId == null ? null : userDirectory.find(studentId).orElse(null);
            if (user == null) {
                return Projections.empty(ReportType.STUDENT_PROFILE.displayName(), criteria);
            }
            List<Map<String, String>> rows = List.of(row(
                    "userId", user.userId(),
                    "firstName", user.firstName(),
                    "lastName", user.lastName(),
                    // Contact values are masked exactly as in the support console (P11 §35, §82).
                    "email", maskEmail(user.email()),
                    "status", user.status()));
            return new ReportProjection(ReportType.STUDENT_PROFILE.displayName(), "Current",
                    appliedFilters(criteria.filters()), criteria.generatedAt(), criteria.callerId(),
                    columns("userId", "User id", "firstName", "First name", "lastName", "Last name",
                            "email", "Email", "status", "Status"),
                    rows, List.of(),
                    "Identity projection only; contact data is masked and no credential is included.");
        }
    }

    /** STUDENT_ATTEMPTS — the student's own attempts with state, timing and published score (§39). */
    @Component
    public static class StudentAttemptsProvider implements ReportDataProvider {

        private final ReportingDeliveryCatalog deliveryCatalog;

        public StudentAttemptsProvider(ReportingDeliveryCatalog deliveryCatalog) {
            this.deliveryCatalog = deliveryCatalog;
        }

        @Override
        public ReportType reportType() {
            return ReportType.STUDENT_ATTEMPTS;
        }

        @Override
        public ReportProjection project(ReportCriteria criteria) {
            UUID studentId = criteria.scopedSubject("studentId");
            List<ReportingDeliveryCatalog.AttemptRow> attempts = deliveryCatalog.attemptsForUser(studentId,
                    criteria.instant("from"), criteria.instant("to"), MAX_ROWS);
            if (attempts.isEmpty()) {
                return Projections.empty(ReportType.STUDENT_ATTEMPTS.displayName(), criteria);
            }
            List<Map<String, String>> rows = new ArrayList<>();
            for (ReportingDeliveryCatalog.AttemptRow attempt : attempts) {
                rows.add(row(
                        "attemptId", text(attempt.attemptId()),
                        "assessmentVersionId", text(attempt.assessmentVersionId()),
                        "mode", attempt.mode(),
                        "status", attempt.status(),
                        "startedOn", text(attempt.startedOn()),
                        "submittedOn", text(attempt.submittedOn()),
                        "totalScore", decimal(attempt.totalScore())));
            }
            return new ReportProjection(ReportType.STUDENT_ATTEMPTS.displayName(), period(criteria.filters()),
                    appliedFilters(criteria.filters()), criteria.generatedAt(), criteria.callerId(),
                    columns("attemptId", "Attempt id", "assessmentVersionId", "Assessment version",
                            "mode", "Mode", "status", "State", "startedOn", "Started", "submittedOn", "Submitted",
                            "totalScore", "Total score"),
                    rows, List.of(),
                    // §39: answer-key detail is never exposed to a student.
                    "Attempt state only; no answer-key or correctness detail is included.");
        }
    }

    /** STUDENT_PERFORMANCE — the frozen student aggregate (§40, §88). */
    @Component
    public static class StudentPerformanceProvider implements ReportDataProvider {

        private final ReportingAnalyticsCatalog analyticsCatalog;

        public StudentPerformanceProvider(ReportingAnalyticsCatalog analyticsCatalog) {
            this.analyticsCatalog = analyticsCatalog;
        }

        @Override
        public ReportType reportType() {
            return ReportType.STUDENT_PERFORMANCE;
        }

        @Override
        public ReportProjection project(ReportCriteria criteria) {
            UUID studentId = criteria.scopedSubject("studentId");
            var analytics = analyticsCatalog.student(studentId).orElse(null);
            if (analytics == null) {
                return Projections.empty(ReportType.STUDENT_PERFORMANCE.displayName(), criteria);
            }
            List<Map<String, String>> rows = List.of(row(
                    "assessmentsAttempted", analytics.assessmentsAttempted(),
                    "averageScore", decimal(analytics.averageScore()),
                    "accuracy", decimal(analytics.accuracy()),
                    "averageTime", decimal(analytics.averageTime()),
                    "lastCalculatedOn", text(analytics.lastCalculatedOn())));
            return new ReportProjection(ReportType.STUDENT_PERFORMANCE.displayName(), "Current",
                    appliedFilters(criteria.filters()), criteria.generatedAt(), criteria.callerId(),
                    columns("assessmentsAttempted", "Assessments attempted", "averageScore", "Average score",
                            "accuracy", "Accuracy", "averageTime", "Average time", "lastCalculatedOn",
                            "Last calculated"),
                    rows, List.of(),
                    freshness(analytics.lastCalculatedOn()));
        }
    }

    /**
     * STUDENT_TOPIC_PERFORMANCE — per-topic accuracy for one student (§41).
     *
     * <p>The frozen {@code topic_analytics} aggregate has topic grain, not student grain, so it cannot
     * answer a per-student topic breakdown. {@code §41} therefore allows a bounded derived query, and
     * that is what this provider performs: it reads the student's own attempts, their immutable
     * snapshots, the authoritative Evaluation outcomes and the authoritative taxonomy, then aggregates
     * accuracy per topic.
     *
     * <p>Taxonomy uncertainty is preserved rather than guessed (§41): a question whose taxonomy is
     * absent or has no topic is reported in an explicit {@code UNMAPPED} row instead of being assigned
     * to an inferred topic. Correctness comes only from the existing Evaluation outcomes
     * ({@code §7.3, §34}); nothing is re-scored.
     */
    @Component
    public static class StudentTopicPerformanceProvider implements ReportDataProvider {

        private static final String UNMAPPED = "UNMAPPED";

        private final ReportingDeliveryCatalog deliveryCatalog;
        private final AttemptAnalyticsCatalog attemptCatalog;
        private final AnalyticsOutcomeCatalog outcomeCatalog;
        private final QuestionTaxonomyCatalog taxonomyCatalog;

        public StudentTopicPerformanceProvider(ReportingDeliveryCatalog deliveryCatalog,
                AttemptAnalyticsCatalog attemptCatalog, AnalyticsOutcomeCatalog outcomeCatalog,
                QuestionTaxonomyCatalog taxonomyCatalog) {
            this.deliveryCatalog = deliveryCatalog;
            this.attemptCatalog = attemptCatalog;
            this.outcomeCatalog = outcomeCatalog;
            this.taxonomyCatalog = taxonomyCatalog;
        }

        @Override
        public ReportType reportType() {
            return ReportType.STUDENT_TOPIC_PERFORMANCE;
        }

        @Override
        public ReportProjection project(ReportCriteria criteria) {
            UUID studentId = criteria.scopedSubject("studentId");
            List<ReportingDeliveryCatalog.AttemptRow> attempts = deliveryCatalog.attemptsForUser(studentId,
                    criteria.instant("from"), criteria.instant("to"), MAX_ROWS);
            if (attempts.isEmpty()) {
                return Projections.empty(ReportType.STUDENT_TOPIC_PERFORMANCE.displayName(), criteria);
            }
            List<UUID> attemptIds = attempts.stream()
                    .map(ReportingDeliveryCatalog.AttemptRow::attemptId).toList();

            // Snapshots identify which question versions the student actually saw.
            List<UUID> questionVersionIds = attemptCatalog.snapshots(attemptIds).stream()
                    .map(AttemptAnalyticsCatalog.SnapshotRow::questionVersionId)
                    .filter(java.util.Objects::nonNull)
                    .distinct()
                    .toList();
            if (questionVersionIds.isEmpty()) {
                return Projections.empty(ReportType.STUDENT_TOPIC_PERFORMANCE.displayName(), criteria);
            }

            // Authoritative correctness from the existing evaluator, and the authoritative taxonomy.
            Map<UUID, Long> correctByVersion = new java.util.HashMap<>();
            Map<UUID, Long> totalByVersion = new java.util.HashMap<>();
            for (AnalyticsOutcomeCatalog.QuestionOutcomeView outcome : outcomeCatalog.questionOutcomes(attemptIds)) {
                if (outcome.questionVersionId() == null) {
                    continue;
                }
                totalByVersion.merge(outcome.questionVersionId(), 1L, Long::sum);
                if ("CORRECT".equalsIgnoreCase(outcome.state())) {
                    correctByVersion.merge(outcome.questionVersionId(), 1L, Long::sum);
                }
            }
            Map<UUID, UUID> questionByVersion = taxonomyCatalog.questionIdByVersionId(questionVersionIds);
            List<UUID> questionIds = questionByVersion.values().stream().filter(java.util.Objects::nonNull)
                    .distinct().toList();
            Map<UUID, UUID> topicByQuestion = new java.util.HashMap<>();
            for (QuestionTaxonomyCatalog.TaxonomyView view : taxonomyCatalog.taxonomy(questionIds)) {
                if (view.topicId() != null) {
                    topicByQuestion.put(view.questionId(), view.topicId());
                }
            }

            // Aggregate per topic; anything without a resolvable topic lands in an explicit UNMAPPED row.
            Map<String, long[]> buckets = new java.util.LinkedHashMap<>();
            for (UUID versionId : questionVersionIds) {
                long total = totalByVersion.getOrDefault(versionId, 0L);
                if (total == 0L) {
                    continue;
                }
                UUID questionId = questionByVersion.get(versionId);
                UUID topicId = questionId == null ? null : topicByQuestion.get(questionId);
                String key = topicId == null ? UNMAPPED : topicId.toString();
                long[] bucket = buckets.computeIfAbsent(key, ignored -> new long[2]);
                bucket[0] += total;
                bucket[1] += correctByVersion.getOrDefault(versionId, 0L);
            }
            if (buckets.isEmpty()) {
                return Projections.empty(ReportType.STUDENT_TOPIC_PERFORMANCE.displayName(), criteria);
            }
            List<Map<String, String>> rows = new ArrayList<>();
            for (Map.Entry<String, long[]> entry : buckets.entrySet()) {
                long total = entry.getValue()[0];
                long correct = entry.getValue()[1];
                rows.add(row(
                        "topicId", entry.getKey(),
                        "attemptedQuestions", total,
                        "correctQuestions", correct,
                        "accuracy", Projections.ratio(correct, total)));
            }
            return new ReportProjection(ReportType.STUDENT_TOPIC_PERFORMANCE.displayName(), period(criteria.filters()),
                    appliedFilters(criteria.filters()), criteria.generatedAt(), criteria.callerId(),
                    columns("topicId", "Topic", "attemptedQuestions", "Questions attempted",
                            "correctQuestions", "Correct", "accuracy", "Accuracy"),
                    rows, List.of(),
                    "Derived from the student's own attempts, snapshots, evaluation outcomes and taxonomy. "
                            + "Questions whose taxonomy has no topic are reported as UNMAPPED, never guessed.");
        }
    }

    /** Masks an email to its first character plus the domain, matching the support console (§82). */
    static String maskEmail(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        int at = value.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        return value.charAt(0) + "***" + value.substring(at);
    }

    /** Freshness disclosure for an aggregate-derived report (§91). */
    static String freshness(Instant calculatedOn) {
        return calculatedOn == null
                ? "Derived from the latest analytics aggregate; calculation time is not reported."
                : "Derived from the analytics aggregate last calculated " + calculatedOn + ".";
    }
}
