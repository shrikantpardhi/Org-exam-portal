package com.company.exam.analytics.service;

import com.company.exam.assessment.api.AssessmentAnalyticsCatalog;
import com.company.exam.content.api.QuestionTaxonomyCatalog;
import com.company.exam.delivery.api.AttemptAnalyticsCatalog;
import com.company.exam.evaluation.api.AnalyticsOutcomeCatalog;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Assembles the immutable source facts for a bounded set of qualified attempts
 * ({@code specs/17-P8-Analytics-V1.5.md §13, §98-§102}).
 *
 * <p>It reads authoritative transactional data only through application contracts, uses bounded
 * batch reads (no per-question query), and never mutates source state ({@code §104, §123}).
 */
@Component
class AnalyticsFactBuilder {

    private final AttemptAnalyticsCatalog delivery;
    private final AssessmentAnalyticsCatalog assessment;
    private final QuestionTaxonomyCatalog content;
    private final AnalyticsOutcomeCatalog outcomes;

    AnalyticsFactBuilder(AttemptAnalyticsCatalog delivery, AssessmentAnalyticsCatalog assessment,
            QuestionTaxonomyCatalog content, AnalyticsOutcomeCatalog outcomes) {
        this.delivery = delivery;
        this.assessment = assessment;
        this.content = content;
        this.outcomes = outcomes;
    }

    /** Builds one fact per qualified attempt, with its evaluated question occurrences. */
    List<AnalyticsFact> build(List<AttemptAnalyticsCatalog.AttemptRow> qualified,
            Map<UUID, BigDecimal> scoreByAttempt) {
        if (qualified.isEmpty()) {
            return List.of();
        }
        Set<UUID> attemptIds = qualified.stream().map(AttemptAnalyticsCatalog.AttemptRow::attemptId)
                .collect(Collectors.toSet());
        Set<UUID> versionsInPlay = qualified.stream()
                .map(AttemptAnalyticsCatalog.AttemptRow::assessmentVersionId).collect(Collectors.toSet());
        Map<UUID, UUID> versionToAssessment = assessment.versions(versionsInPlay).stream()
                .collect(Collectors.toMap(AssessmentAnalyticsCatalog.VersionRow::versionId,
                        AssessmentAnalyticsCatalog.VersionRow::assessmentId, (a, b) -> a));

        Map<UUID, List<AttemptAnalyticsCatalog.SnapshotRow>> snapshotsByAttempt = delivery.snapshots(attemptIds)
                .stream().collect(Collectors.groupingBy(AttemptAnalyticsCatalog.SnapshotRow::attemptId));
        Map<UUID, Map<UUID, AttemptAnalyticsCatalog.ResponseRow>> responsesByAttempt =
                delivery.responses(attemptIds).stream().collect(Collectors.groupingBy(
                        AttemptAnalyticsCatalog.ResponseRow::attemptId,
                        Collectors.toMap(AttemptAnalyticsCatalog.ResponseRow::questionVersionId,
                                Function.identity(), (a, b) -> a)));

        Set<UUID> questionVersionIds = snapshotsByAttempt.values().stream().flatMap(List::stream)
                .map(AttemptAnalyticsCatalog.SnapshotRow::questionVersionId).collect(Collectors.toSet());
        Map<UUID, UUID> questionIdByVersion = content.questionIdByVersionId(questionVersionIds);
        Set<UUID> questionIds = questionIdByVersion.values().stream().collect(Collectors.toSet());
        Map<UUID, UUID> topicByQuestion = new HashMap<>();
        for (QuestionTaxonomyCatalog.TaxonomyView taxonomy : content.taxonomy(questionIds)) {
            if (taxonomy.topicId() != null) {
                topicByQuestion.put(taxonomy.questionId(), taxonomy.topicId());
            }
        }

        Map<UUID, Map<UUID, AnalyticsOutcomeCatalog.QuestionOutcomeView>> outcomeByAttempt = new HashMap<>();
        for (AnalyticsOutcomeCatalog.QuestionOutcomeView view : outcomes.questionOutcomes(attemptIds)) {
            outcomeByAttempt.computeIfAbsent(view.attemptId(), key -> new HashMap<>())
                    .put(view.questionVersionId(), view);
        }

        List<AnalyticsFact> facts = new ArrayList<>(qualified.size());
        for (AttemptAnalyticsCatalog.AttemptRow attempt : qualified) {
            Map<UUID, AnalyticsOutcomeCatalog.QuestionOutcomeView> attemptOutcomes =
                    outcomeByAttempt.getOrDefault(attempt.attemptId(), Map.of());
            Map<UUID, AttemptAnalyticsCatalog.ResponseRow> attemptResponses =
                    responsesByAttempt.getOrDefault(attempt.attemptId(), Map.of());
            List<AnalyticsFact.QuestionFact> questions = new ArrayList<>();
            for (AttemptAnalyticsCatalog.SnapshotRow snapshot
                    : snapshotsByAttempt.getOrDefault(attempt.attemptId(), List.of())) {
                UUID questionId = questionIdByVersion.get(snapshot.questionVersionId());
                AnalyticsOutcomeCatalog.QuestionOutcomeView outcome = attemptOutcomes.get(snapshot.questionVersionId());
                AttemptAnalyticsCatalog.ResponseRow response = attemptResponses.get(snapshot.questionVersionId());
                questions.add(new AnalyticsFact.QuestionFact(snapshot.questionVersionId(), questionId,
                        questionId == null ? null : topicByQuestion.get(questionId), snapshot.sectionName(),
                        outcome == null ? "NOT_EVALUATED" : outcome.state(),
                        outcome == null ? null : outcome.awardedMarks(), snapshot.marks(),
                        response == null ? null : response.timeSpentSeconds(),
                        response != null && response.answered()));
            }
            facts.add(new AnalyticsFact(attempt.attemptId(), attempt.userId(),
                    versionToAssessment.get(attempt.assessmentVersionId()), attempt.assessmentVersionId(),
                    scoreByAttempt.get(attempt.attemptId()), questions));
        }
        return facts;
    }
}
