package com.company.exam.reporting.projection;

import static com.company.exam.reporting.projection.Projections.MAX_ROWS;
import static com.company.exam.reporting.projection.Projections.appliedFilters;
import static com.company.exam.reporting.projection.Projections.columns;
import static com.company.exam.reporting.projection.Projections.row;
import static com.company.exam.reporting.projection.Projections.text;

import com.company.exam.content.api.ReportingContentCatalog;
import com.company.exam.reporting.catalog.ReportType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Content report projections ({@code specs/22-P13-Reporting-V1.5.md §49-§52}).
 *
 * <p>Inventory, version lifecycle and tag coverage are read through the Content contract and never
 * through Content repositories ({@code §10}). No answer key or correctness data is included
 * ({@code §34}); every read is bounded ({@code §7.6}).
 */
public final class ContentReportProviders {

    private ContentReportProviders() {
    }

    /** CONTENT_QUESTION_INVENTORY — questions by exam, subject and taxonomy (§50). */
    @Component
    public static class QuestionInventoryProvider implements ReportDataProvider {

        private final ReportingContentCatalog contentCatalog;

        public QuestionInventoryProvider(ReportingContentCatalog contentCatalog) {
            this.contentCatalog = contentCatalog;
        }

        @Override
        public ReportType reportType() {
            return ReportType.CONTENT_QUESTION_INVENTORY;
        }

        @Override
        public ReportProjection project(ReportCriteria criteria) {
            List<ReportingContentCatalog.QuestionRow> questions = contentCatalog.questions(
                    criteria.uuid("examId"), criteria.uuid("subjectId"), MAX_ROWS);
            if (questions.isEmpty()) {
                return Projections.empty(ReportType.CONTENT_QUESTION_INVENTORY.displayName(), criteria);
            }
            // Versions let the inventory show each question's latest lifecycle state (§50, §51).
            Map<UUID, Integer> latestVersion = new HashMap<>();
            Map<UUID, String> latestState = new HashMap<>();
            for (ReportingContentCatalog.QuestionVersionRow version : contentCatalog.versionsOf(
                    questions.stream().map(ReportingContentCatalog.QuestionRow::questionId).toList())) {
                Integer current = latestVersion.get(version.questionId());
                if (current == null || (version.version() != null && version.version() > current)) {
                    latestVersion.put(version.questionId(), version.version());
                    latestState.put(version.questionId(), version.reviewStatus());
                }
            }
            List<Map<String, String>> rows = new ArrayList<>();
            for (ReportingContentCatalog.QuestionRow question : questions) {
                rows.add(row(
                        "questionId", text(question.questionId()),
                        "examId", text(question.examId()),
                        "subjectId", text(question.subjectId()),
                        "chapterId", text(question.chapterId()),
                        "topicId", text(question.topicId()),
                        "subTopicId", text(question.subTopicId()),
                        "status", question.status(),
                        "latestVersion", latestVersion.get(question.questionId()),
                        "latestState", latestState.get(question.questionId())));
            }
            return new ReportProjection(ReportType.CONTENT_QUESTION_INVENTORY.displayName(), "Current",
                    appliedFilters(criteria.filters()), criteria.generatedAt(), criteria.callerId(),
                    columns("questionId", "Question id", "examId", "Exam", "subjectId", "Subject",
                            "chapterId", "Chapter", "topicId", "Topic", "subTopicId", "Sub-topic",
                            "status", "Status", "latestVersion", "Latest version", "latestState", "Latest state"),
                    rows, List.of(), "Inventory of authoring records; no answer key or correctness is included.");
        }
    }

    /** CONTENT_VERSION_STATUS — the lifecycle state of question versions (§51). */
    @Component
    public static class QuestionVersionStatusProvider implements ReportDataProvider {

        private final ReportingContentCatalog contentCatalog;

        public QuestionVersionStatusProvider(ReportingContentCatalog contentCatalog) {
            this.contentCatalog = contentCatalog;
        }

        @Override
        public ReportType reportType() {
            return ReportType.CONTENT_VERSION_STATUS;
        }

        @Override
        public ReportProjection project(ReportCriteria criteria) {
            List<ReportingContentCatalog.QuestionRow> questions = contentCatalog.questions(null, null, MAX_ROWS);
            if (questions.isEmpty()) {
                return Projections.empty(ReportType.CONTENT_VERSION_STATUS.displayName(), criteria);
            }
            List<ReportingContentCatalog.QuestionVersionRow> versions = contentCatalog.versionsOf(
                    questions.stream().map(ReportingContentCatalog.QuestionRow::questionId).toList());
            List<Map<String, String>> rows = new ArrayList<>();
            // Count by lifecycle state for the summary metrics.
            Map<String, Long> byState = new LinkedHashMap<>();
            for (ReportingContentCatalog.QuestionVersionRow version : versions) {
                String state = version.reviewStatus() == null ? "UNKNOWN" : version.reviewStatus();
                byState.merge(state, 1L, Long::sum);
                rows.add(row(
                        "questionVersionId", text(version.questionVersionId()),
                        "questionId", text(version.questionId()),
                        "version", version.version(),
                        "reviewStatus", state,
                        "published", version.published(),
                        "difficulty", version.difficulty()));
            }
            if (rows.isEmpty()) {
                return Projections.empty(ReportType.CONTENT_VERSION_STATUS.displayName(), criteria);
            }
            List<ReportProjection.SummaryMetric> metrics = new ArrayList<>();
            for (Map.Entry<String, Long> entry : byState.entrySet()) {
                metrics.add(new ReportProjection.SummaryMetric("Versions · " + entry.getKey(),
                        Long.toString(entry.getValue()),
                        "Count of question versions in review state " + entry.getKey()));
            }
            return new ReportProjection(ReportType.CONTENT_VERSION_STATUS.displayName(), "Current",
                    appliedFilters(criteria.filters()), criteria.generatedAt(), criteria.callerId(),
                    columns("questionVersionId", "Question version", "questionId", "Question", "version", "Version",
                            "reviewStatus", "Review state", "published", "Published", "difficulty", "Difficulty"),
                    rows, metrics, "Lifecycle state as recorded by Content; no state is inferred or changed.");
        }
    }

    /** CONTENT_TAG_COVERAGE — tagged versus untagged questions per tag (§52). */
    @Component
    public static class TagCoverageProvider implements ReportDataProvider {

        private final ReportingContentCatalog contentCatalog;

        public TagCoverageProvider(ReportingContentCatalog contentCatalog) {
            this.contentCatalog = contentCatalog;
        }

        @Override
        public ReportType reportType() {
            return ReportType.CONTENT_TAG_COVERAGE;
        }

        @Override
        public ReportProjection project(ReportCriteria criteria) {
            List<ReportingContentCatalog.QuestionRow> questions = contentCatalog.questions(null, null, MAX_ROWS);
            if (questions.isEmpty()) {
                return Projections.empty(ReportType.CONTENT_TAG_COVERAGE.displayName(), criteria);
            }
            List<UUID> questionIds = questions.stream()
                    .map(ReportingContentCatalog.QuestionRow::questionId).toList();
            List<ReportingContentCatalog.QuestionTagRow> tagged = contentCatalog.questionTags(MAX_ROWS);

            Map<UUID, Long> countByTag = new LinkedHashMap<>();
            java.util.Set<UUID> taggedQuestions = new java.util.HashSet<>();
            for (ReportingContentCatalog.QuestionTagRow link : tagged) {
                if (!questionIds.contains(link.questionId())) {
                    continue;
                }
                taggedQuestions.add(link.questionId());
                countByTag.merge(link.tagId(), 1L, Long::sum);
            }
            List<Map<String, String>> rows = new ArrayList<>();
            for (Map.Entry<UUID, Long> entry : countByTag.entrySet()) {
                rows.add(row("tagId", text(entry.getKey()), "taggedQuestions", entry.getValue()));
            }
            long untagged = questionIds.size() - taggedQuestions.size();
            rows.add(row("tagId", "UNTAGGED", "taggedQuestions", untagged));
            return new ReportProjection(ReportType.CONTENT_TAG_COVERAGE.displayName(), "Current",
                    appliedFilters(criteria.filters()), criteria.generatedAt(), criteria.callerId(),
                    columns("tagId", "Tag", "taggedQuestions", "Questions carrying the tag"),
                    rows, List.of(new ReportProjection.SummaryMetric("Untagged questions",
                            Long.toString(untagged),
                            "Questions in scope with no question_tag association")),
                    "Coverage counts the authoritative question_tag associations; UNTAGGED is reported "
                            + "explicitly rather than omitted (§52).");
        }
    }
}
