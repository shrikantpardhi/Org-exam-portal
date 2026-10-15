package com.company.exam.analytics.service;

import com.company.exam.analytics.config.AnalyticsProperties;
import com.company.exam.analytics.dto.AnalyticsDtos;
import com.company.exam.analytics.entity.AssessmentAnalytics;
import com.company.exam.analytics.entity.OrganizationAnalytics;
import com.company.exam.analytics.entity.QuestionAnalytics;
import com.company.exam.analytics.entity.StudentAnalytics;
import com.company.exam.analytics.entity.TopicAnalytics;
import com.company.exam.analytics.observability.AnalyticsMetrics;
import com.company.exam.analytics.repository.AssessmentAnalyticsRepository;
import com.company.exam.analytics.repository.OrganizationAnalyticsRepository;
import com.company.exam.analytics.repository.QuestionAnalyticsRepository;
import com.company.exam.analytics.repository.StudentAnalyticsRepository;
import com.company.exam.analytics.repository.TopicAnalyticsRepository;
import com.company.exam.assessment.api.AssessmentAnalyticsCatalog;
import com.company.exam.common.cache.CacheService;
import com.company.exam.content.api.QuestionTaxonomyCatalog;
import com.company.exam.delivery.api.AttemptAnalyticsCatalog;
import com.company.exam.evaluation.api.AnalyticsOutcomeCatalog;
import com.company.exam.identity.api.AnalyticsIdentityCatalog;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
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
 * Analytics dashboard read models ({@code specs/17-P8-Analytics-V1.5.md §50-§63, §111, §137-§141}).
 *
 * <p>Frozen aggregates are served from the analytics tables; metrics the frozen storage cannot
 * represent are derived by bounded read queries ({@code §112}). Reads are cached with a
 * scope-bearing key; the cache is never authoritative, so a cache outage degrades to a PostgreSQL
 * read rather than an error ({@code §62, §63, §2015}).
 */
@Service
public class AnalyticsQueryService {

    private static final Duration CACHE_TTL = Duration.ofSeconds(30);

    private final StudentAnalyticsRepository studentRepository;
    private final AssessmentAnalyticsRepository assessmentRepository;
    private final QuestionAnalyticsRepository questionRepository;
    private final TopicAnalyticsRepository topicRepository;
    private final OrganizationAnalyticsRepository organizationRepository;
    private final AssessmentAnalyticsCatalog assessment;
    private final AttemptAnalyticsCatalog delivery;
    private final QuestionTaxonomyCatalog content;
    private final AnalyticsOutcomeCatalog outcomes;
    private final AnalyticsIdentityCatalog identity;
    private final AnalyticsFactBuilder factBuilder;
    private final AnalyticsSectionService sectionService;
    private final CacheService cache;
    private final AnalyticsMetrics metrics;
    private final AnalyticsProperties properties;

    public AnalyticsQueryService(StudentAnalyticsRepository studentRepository,
            AssessmentAnalyticsRepository assessmentRepository, QuestionAnalyticsRepository questionRepository,
            TopicAnalyticsRepository topicRepository, OrganizationAnalyticsRepository organizationRepository,
            AssessmentAnalyticsCatalog assessment, AttemptAnalyticsCatalog delivery, QuestionTaxonomyCatalog content,
            AnalyticsOutcomeCatalog outcomes, AnalyticsIdentityCatalog identity, AnalyticsFactBuilder factBuilder,
            AnalyticsSectionService sectionService, CacheService cache, AnalyticsMetrics metrics,
            AnalyticsProperties properties) {
        this.studentRepository = studentRepository;
        this.assessmentRepository = assessmentRepository;
        this.questionRepository = questionRepository;
        this.topicRepository = topicRepository;
        this.organizationRepository = organizationRepository;
        this.assessment = assessment;
        this.delivery = delivery;
        this.content = content;
        this.outcomes = outcomes;
        this.identity = identity;
        this.factBuilder = factBuilder;
        this.sectionService = sectionService;
        this.cache = cache;
        this.metrics = metrics;
        this.properties = properties;
    }

    // ------------------------------------------------------------------ student

    @Transactional(readOnly = true)
    public AnalyticsDtos.StudentAnalyticsResponse student(UUID userId) {
        return cached(AnalyticsCacheKeys.student(userId), AnalyticsDtos.StudentAnalyticsResponse.class,
                () -> studentRepository.findByUserId(userId).map(this::toResponse)
                        .orElseGet(() -> new AnalyticsDtos.StudentAnalyticsResponse(null, null, null, null,
                                null, "EMPTY")));
    }

    @Transactional(readOnly = true)
    public AnalyticsDtos.StudentDashboard studentDashboard(UUID userId) {
        return new AnalyticsDtos.StudentDashboard(student(userId), studentAssessments(userId), studentTopics(userId));
    }

    @Transactional(readOnly = true)
    public List<AnalyticsDtos.StudentAssessmentItem> studentAssessments(UUID userId) {
        List<AttemptAnalyticsCatalog.AttemptRow> attempts = delivery.attemptsForUsers(List.of(userId));
        Map<UUID, AnalyticsOutcomeCatalog.ScoreRow> scores = scoreByAttempt(attempts);
        List<AnalyticsDtos.StudentAssessmentItem> items = new ArrayList<>();
        for (AttemptAnalyticsCatalog.AttemptRow attempt : attempts) {
            AnalyticsOutcomeCatalog.ScoreRow score = scores.get(attempt.attemptId());
            if (score == null) {
                continue;
            }
            items.add(new AnalyticsDtos.StudentAssessmentItem(resolveAssessmentId(attempt), attempt.attemptId(),
                    attempt.assessmentVersionId(), attempt.attemptMode(), score.obtainedMarks(),
                    score.percentage(), score.resultStatus(), attempt.startedOn()));
        }
        return items;
    }

    @Transactional(readOnly = true)
    public List<AnalyticsDtos.StudentTopicItem> studentTopics(UUID userId) {
        List<AnalyticsFact> facts = qualifiedFacts(delivery.attemptsForUsers(List.of(userId)));
        Map<UUID, long[]> counts = new HashMap<>();
        Map<UUID, List<BigDecimal>> awarded = new HashMap<>();
        for (AnalyticsFact fact : facts) {
            for (AnalyticsFact.QuestionFact question : fact.questions()) {
                if (question.topicId() == null || !question.counted()) {
                    continue;
                }
                long[] pair = counts.computeIfAbsent(question.topicId(), key -> new long[2]);
                if (question.correct()) {
                    pair[0]++;
                } else {
                    pair[1]++;
                }
                if (question.awardedMarks() != null) {
                    awarded.computeIfAbsent(question.topicId(), key -> new ArrayList<>())
                            .add(question.awardedMarks());
                }
            }
        }
        List<AnalyticsDtos.StudentTopicItem> items = new ArrayList<>(counts.size());
        for (Map.Entry<UUID, long[]> entry : counts.entrySet()) {
            long evaluated = entry.getValue()[0] + entry.getValue()[1];
            BigDecimal accuracy = AnalyticsMath.accuracy(entry.getValue()[0], entry.getValue()[1]);
            items.add(new AnalyticsDtos.StudentTopicItem(entry.getKey(), (int) evaluated, accuracy,
                    AnalyticsMath.score(AnalyticsMath.mean(awarded.getOrDefault(entry.getKey(), List.of()))),
                    evaluated >= properties.getMinimumSampleSize(),
                    accuracy != null && accuracy.doubleValue() < properties.getWeakAreaAccuracyThreshold()));
        }
        items.sort(java.util.Comparator.comparing(AnalyticsDtos.StudentTopicItem::accuracy,
                java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())));
        return items;
    }

    // ------------------------------------------------------------------ assessment

    @Transactional(readOnly = true)
    public AnalyticsDtos.AssessmentDashboard assessmentDashboard(UUID assessmentId) {
        return new AnalyticsDtos.AssessmentDashboard(assessment(assessmentId), sections(assessmentId));
    }

    @Transactional(readOnly = true)
    public AnalyticsDtos.AssessmentAnalyticsResponse assessment(UUID assessmentId) {
        return cached(AnalyticsCacheKeys.assessment(assessmentId), AnalyticsDtos.AssessmentAnalyticsResponse.class,
                () -> assessmentRepository.findByAssessmentId(assessmentId).map(this::toResponse)
                        .orElseGet(() -> new AnalyticsDtos.AssessmentAnalyticsResponse(assessmentId, null, null, null,
                                null, null, "EMPTY")));
    }

    @Transactional(readOnly = true)
    public List<AnalyticsDtos.SectionAnalyticsView> sections(UUID assessmentId) {
        return sectionService.sections(assessmentId);
    }

    @Transactional(readOnly = true)
    public List<AnalyticsDtos.QuestionAnalyticsResponse> assessmentQuestions(UUID assessmentId) {
        List<UUID> versionIds = assessment.allVersions().stream()
                .filter(version -> assessmentId.equals(version.assessmentId()))
                .map(AssessmentAnalyticsCatalog.VersionRow::versionId).toList();
        List<UUID> questionVersionIds = assessment.questionsForVersions(versionIds).stream()
                .map(AssessmentAnalyticsCatalog.QuestionRow::questionVersionId).toList();
        Map<UUID, UUID> questionIdByVersion = content.questionIdByVersionId(questionVersionIds);
        List<UUID> questionIds = questionIdByVersion.values().stream().distinct().toList();
        return questionRepository.findByQuestionIdIn(questionIds).stream().map(this::toResponse).toList();
    }

    // ------------------------------------------------------------------ question

    @Transactional(readOnly = true)
    public AnalyticsDtos.QuestionAnalyticsResponse question(UUID questionId) {
        return cached(AnalyticsCacheKeys.question(questionId), AnalyticsDtos.QuestionAnalyticsResponse.class,
                () -> questionRepository.findByQuestionId(questionId).map(this::toResponse)
                        .orElseGet(() -> new AnalyticsDtos.QuestionAnalyticsResponse(questionId, null, null, null, null,
                                null, null, null, null, null, "EMPTY")));
    }

    // ------------------------------------------------------------------ topic

    @Transactional(readOnly = true)
    public AnalyticsDtos.TopicAnalyticsResponse topic(UUID topicId) {
        return cached(AnalyticsCacheKeys.topic(topicId), AnalyticsDtos.TopicAnalyticsResponse.class,
                () -> topicRepository.findByTopicId(topicId).map(this::toResponse)
                        .orElseGet(() -> new AnalyticsDtos.TopicAnalyticsResponse(topicId, null, null, null, 0, false,
                                null, "EMPTY")));
    }

    // ------------------------------------------------------------------ organization

    @Transactional(readOnly = true)
    public AnalyticsDtos.OrganizationAnalyticsResponse organization() {
        List<UUID> organizations = identity.organizationIds();
        if (organizations.isEmpty()) {
            return new AnalyticsDtos.OrganizationAnalyticsResponse(null, null, null, null, null, null, "EMPTY");
        }
        UUID organizationId = organizations.get(0);
        return cached(AnalyticsCacheKeys.organization(organizationId),
                AnalyticsDtos.OrganizationAnalyticsResponse.class,
                () -> organizationRepository.findByOrganizationId(organizationId).map(this::toResponse)
                        .orElseGet(() -> new AnalyticsDtos.OrganizationAnalyticsResponse(organizationId, null, null,
                                null, null, null, "EMPTY")));
    }

    // ------------------------------------------------------------------ internals

    private List<AnalyticsFact> qualifiedFacts(List<AttemptAnalyticsCatalog.AttemptRow> attempts) {
        if (attempts.isEmpty()) {
            return List.of();
        }
        Map<UUID, AnalyticsOutcomeCatalog.ScoreRow> scores = scoreByAttempt(attempts);
        List<AttemptAnalyticsCatalog.AttemptRow> qualified = attempts.stream()
                .filter(attempt -> scores.containsKey(attempt.attemptId())).toList();
        Map<UUID, BigDecimal> scoreByAttempt = scores.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().obtainedMarks(),
                        (a, b) -> a));
        return factBuilder.build(qualified, scoreByAttempt);
    }

    private Map<UUID, AnalyticsOutcomeCatalog.ScoreRow> scoreByAttempt(
            List<AttemptAnalyticsCatalog.AttemptRow> attempts) {
        return outcomes.currentScores(attempts.stream().map(AttemptAnalyticsCatalog.AttemptRow::attemptId).toList())
                .stream().collect(Collectors.toMap(AnalyticsOutcomeCatalog.ScoreRow::attemptId, Function.identity(),
                        (a, b) -> a));
    }

    private UUID resolveAssessmentId(AttemptAnalyticsCatalog.AttemptRow attempt) {
        return assessment.versions(List.of(attempt.assessmentVersionId())).stream()
                .map(AssessmentAnalyticsCatalog.VersionRow::assessmentId).findFirst().orElse(null);
    }

    private <T> T cached(String key, Class<T> type, java.util.function.Supplier<T> loader) {
        var hit = cache.get(key, type);
        if (hit.isPresent()) {
            metrics.cacheHit();
            return hit.get();
        }
        metrics.cacheMiss();
        T value = loader.get();
        if (value != null) {
            cache.put(key, value, CACHE_TTL);
        }
        return value;
    }

    private String freshness(Instant lastCalculatedOn) {
        if (lastCalculatedOn == null) {
            return "EMPTY";
        }
        return lastCalculatedOn.isBefore(Instant.now().minus(properties.getStaleAfter())) ? "STALE" : "CURRENT";
    }

    private AnalyticsDtos.StudentAnalyticsResponse toResponse(StudentAnalytics row) {
        return new AnalyticsDtos.StudentAnalyticsResponse(row.getAssessmentsAttempted(), row.getAverageScore(),
                row.getAccuracy(), row.getAverageTime(), row.getLastCalculatedOn(),
                freshness(row.getLastCalculatedOn()));
    }

    private AnalyticsDtos.AssessmentAnalyticsResponse toResponse(AssessmentAnalytics row) {
        return new AnalyticsDtos.AssessmentAnalyticsResponse(row.getAssessmentId(), row.getTotalAttempts(),
                row.getAverageScore(), row.getHighestScore(), row.getLowestScore(), row.getLastCalculatedOn(),
                freshness(row.getLastCalculatedOn()));
    }

    private AnalyticsDtos.QuestionAnalyticsResponse toResponse(QuestionAnalytics row) {
        int correct = row.getCorrectAttempts() == null ? 0 : row.getCorrectAttempts();
        int incorrect = row.getIncorrectAttempts() == null ? 0 : row.getIncorrectAttempts();
        long total = correct + incorrect;
        BigDecimal skipped = skippedPercentage(row.getQuestionId());
        return new AnalyticsDtos.QuestionAnalyticsResponse(row.getQuestionId(), row.getTotalAttempts(),
                row.getCorrectAttempts(), row.getIncorrectAttempts(), row.getAverageTime(), row.getDifficultyIndex(),
                AnalyticsMath.share(correct, total), AnalyticsMath.share(incorrect, total), skipped,
                row.getLastCalculatedOn(), freshness(row.getLastCalculatedOn()));
    }

    /** Skip rate derived from the AttemptQuestion population minus qualifying answered responses ({@code §33}). */
    private BigDecimal skippedPercentage(UUID questionId) {
        List<UUID> versionIds = content.versionIdsByQuestionId(List.of(questionId))
                .getOrDefault(questionId, List.of());
        if (versionIds.isEmpty()) {
            return null;
        }
        long snapshots = 0;
        long answered = 0;
        for (AttemptAnalyticsCatalog.QuestionPopulation population : delivery.questionPopulation(versionIds)) {
            snapshots += population.snapshotCount();
            answered += population.answeredCount();
        }
        return AnalyticsMath.share(snapshots - answered, snapshots);
    }

    private AnalyticsDtos.TopicAnalyticsResponse toResponse(TopicAnalytics row) {
        int sample = row.getTotalAttempts() == null ? 0 : row.getTotalAttempts();
        return new AnalyticsDtos.TopicAnalyticsResponse(row.getTopicId(), row.getTotalAttempts(),
                row.getAverageScore(), row.getAccuracy(), sample, sample >= properties.getMinimumSampleSize(),
                row.getLastCalculatedOn(), freshness(row.getLastCalculatedOn()));
    }

    private AnalyticsDtos.OrganizationAnalyticsResponse toResponse(OrganizationAnalytics row) {
        return new AnalyticsDtos.OrganizationAnalyticsResponse(row.getOrganizationId(), row.getTotalStudents(),
                row.getTotalAssessments(), row.getTotalAttempts(), row.getActiveUsers(), row.getLastCalculatedOn(),
                freshness(row.getLastCalculatedOn()));
    }
}
