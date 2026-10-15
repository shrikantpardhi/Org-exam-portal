package com.company.exam.analytics.service;

import com.company.exam.analytics.dto.AnalyticsDtos;
import com.company.exam.assessment.api.AssessmentAnalyticsCatalog;
import com.company.exam.delivery.api.AttemptAnalyticsCatalog;
import com.company.exam.evaluation.api.AnalyticsOutcomeCatalog;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The dynamic section read model ({@code specs/17-P8-Analytics-V1.5.md §45-§48}).
 *
 * <p>Section analytics is derived from {@code AttemptQuestion.section_name}, the persisted question
 * time and the evaluation outcomes. No section analytics table is introduced ({@code §45}). Section
 * names are scoped to the assessment version; a global section name is not assumed unique
 * ({@code §47}).
 */
@Service
public class AnalyticsSectionService {

    private static final String UNSECTIONED = "UNSECTIONED";

    private final AssessmentAnalyticsCatalog assessment;
    private final AttemptAnalyticsCatalog delivery;
    private final AnalyticsOutcomeCatalog outcomes;
    private final AnalyticsFactBuilder factBuilder;

    public AnalyticsSectionService(AssessmentAnalyticsCatalog assessment, AttemptAnalyticsCatalog delivery,
            AnalyticsOutcomeCatalog outcomes, AnalyticsFactBuilder factBuilder) {
        this.assessment = assessment;
        this.delivery = delivery;
        this.outcomes = outcomes;
        this.factBuilder = factBuilder;
    }

    /** Section-level aggregates for an assessment ({@code §46-§48}). */
    @Transactional(readOnly = true)
    public List<AnalyticsDtos.SectionAnalyticsView> sections(UUID assessmentId) {
        List<UUID> versionIds = assessment.allVersions().stream()
                .filter(version -> assessmentId.equals(version.assessmentId()))
                .map(AssessmentAnalyticsCatalog.VersionRow::versionId)
                .toList();
        if (versionIds.isEmpty()) {
            return List.of();
        }
        List<AttemptAnalyticsCatalog.AttemptRow> attempts = delivery.attemptsForVersions(versionIds);
        if (attempts.isEmpty()) {
            return List.of();
        }
        Map<UUID, AnalyticsOutcomeCatalog.ScoreRow> scores = outcomes
                .currentScores(attempts.stream().map(AttemptAnalyticsCatalog.AttemptRow::attemptId).toList())
                .stream().collect(Collectors.toMap(AnalyticsOutcomeCatalog.ScoreRow::attemptId, Function.identity(),
                        (a, b) -> a));
        List<AttemptAnalyticsCatalog.AttemptRow> qualified = attempts.stream()
                .filter(attempt -> scores.containsKey(attempt.attemptId())).toList();
        Map<UUID, BigDecimal> scoreByAttempt = scores.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().obtainedMarks(),
                        (a, b) -> a));
        List<AnalyticsFact> facts = factBuilder.build(qualified, scoreByAttempt);

        Map<String, SectionAccumulator> bySection = new HashMap<>();
        for (AnalyticsFact fact : facts) {
            for (AnalyticsFact.QuestionFact question : fact.questions()) {
                String section = question.sectionName() == null ? UNSECTIONED : question.sectionName();
                bySection.computeIfAbsent(section, key -> new SectionAccumulator()).add(question);
            }
        }
        List<AnalyticsDtos.SectionAnalyticsView> views = new ArrayList<>(bySection.size());
        bySection.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry ->
                views.add(entry.getValue().toView(entry.getKey())));
        return views;
    }

    /** Mutable per-section accumulator; the section aggregates are computed once per read. */
    private static final class SectionAccumulator {
        private int questionCount;
        private BigDecimal totalMarks = BigDecimal.ZERO;
        private BigDecimal obtainedMarks = BigDecimal.ZERO;
        private long correct;
        private long incorrect;
        private final List<BigDecimal> times = new ArrayList<>();

        void add(AnalyticsFact.QuestionFact question) {
            questionCount++;
            if (question.marks() != null) {
                totalMarks = totalMarks.add(question.marks());
            }
            if (question.counted() && question.awardedMarks() != null) {
                obtainedMarks = obtainedMarks.add(question.awardedMarks());
            }
            if (question.correct()) {
                correct++;
            } else if (question.counted()) {
                incorrect++;
            }
            if (question.timeSpentSeconds() != null && question.timeSpentSeconds() >= 0) {
                times.add(BigDecimal.valueOf(question.timeSpentSeconds()));
            }
        }

        AnalyticsDtos.SectionAnalyticsView toView(String sectionName) {
            return new AnalyticsDtos.SectionAnalyticsView(sectionName, questionCount,
                    AnalyticsMath.score(totalMarks), AnalyticsMath.score(obtainedMarks),
                    AnalyticsMath.accuracy(correct, incorrect), AnalyticsMath.time(AnalyticsMath.mean(times)));
        }
    }
}
