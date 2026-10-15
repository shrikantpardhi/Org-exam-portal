package com.company.exam.analytics.service;

import com.company.exam.analytics.config.AnalyticsProperties;
import com.company.exam.analytics.entity.AssessmentAnalytics;
import com.company.exam.analytics.entity.OrganizationAnalytics;
import com.company.exam.analytics.entity.QuestionAnalytics;
import com.company.exam.analytics.entity.StudentAnalytics;
import com.company.exam.analytics.entity.TopicAnalytics;
import com.company.exam.analytics.observability.AnalyticsMetrics;
import com.company.exam.common.cache.CacheService;
import com.company.exam.analytics.repository.AssessmentAnalyticsRepository;
import com.company.exam.analytics.repository.OrganizationAnalyticsRepository;
import com.company.exam.analytics.repository.QuestionAnalyticsRepository;
import com.company.exam.analytics.repository.StudentAnalyticsRepository;
import com.company.exam.analytics.repository.TopicAnalyticsRepository;
import com.company.exam.assessment.api.AssessmentAnalyticsCatalog;
import com.company.exam.delivery.api.AttemptAnalyticsCatalog;
import com.company.exam.evaluation.api.AnalyticsOutcomeCatalog;
import com.company.exam.identity.api.AnalyticsIdentityCatalog;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Incremental refresh and full rebuild of the analytics aggregates
 * ({@code specs/17-P8-Analytics-V1.5.md §10, §11, §64-§67, §93-§103}).
 *
 * <p>Every metric is recomputed from authoritative transactional data and upserted
 * ({@code §95-§97}); no counter is incremented blindly, so a rebuild is deterministic and idempotent
 * and a formula change is recoverable by a full rebuild ({@code §92}). The service never writes to a
 * transactional table.
 */
@Service
public class AnalyticsRefreshService {

    private final AttemptAnalyticsCatalog delivery;
    private final AssessmentAnalyticsCatalog assessment;
    private final AnalyticsOutcomeCatalog outcomes;
    private final AnalyticsIdentityCatalog identity;
    private final AnalyticsCalculator calculator;
    private final AnalyticsFactBuilder factBuilder;
    private final StudentAnalyticsRepository studentRepository;
    private final AssessmentAnalyticsRepository assessmentRepository;
    private final QuestionAnalyticsRepository questionRepository;
    private final TopicAnalyticsRepository topicRepository;
    private final OrganizationAnalyticsRepository organizationRepository;
    private final AnalyticsProperties properties;
    private final AnalyticsMetrics metrics;
    private final CacheService cache;

    public AnalyticsRefreshService(AttemptAnalyticsCatalog delivery, AssessmentAnalyticsCatalog assessment,
            AnalyticsOutcomeCatalog outcomes, AnalyticsIdentityCatalog identity, AnalyticsCalculator calculator,
            AnalyticsFactBuilder factBuilder, StudentAnalyticsRepository studentRepository,
            AssessmentAnalyticsRepository assessmentRepository, QuestionAnalyticsRepository questionRepository,
            TopicAnalyticsRepository topicRepository, OrganizationAnalyticsRepository organizationRepository,
            AnalyticsProperties properties, AnalyticsMetrics metrics, CacheService cache) {
        this.delivery = delivery;
        this.assessment = assessment;
        this.outcomes = outcomes;
        this.identity = identity;
        this.calculator = calculator;
        this.factBuilder = factBuilder;
        this.studentRepository = studentRepository;
        this.assessmentRepository = assessmentRepository;
        this.questionRepository = questionRepository;
        this.topicRepository = topicRepository;
        this.organizationRepository = organizationRepository;
        this.properties = properties;
        this.metrics = metrics;
        this.cache = cache;
    }

    /** Full rebuild of every grain ({@code §65, §10}). Deterministic: identical sources -> identical rows. */
    @Transactional
    public int rebuildAll() {
        long startedAt = System.nanoTime();
        int upserts = recalculate(Set.of(), true);
        metrics.rebuildRan(Duration.ofNanos(System.nanoTime() - startedAt));
        return upserts;
    }

    /** Bounded incremental refresh scoped to the impacted attempt ({@code §66}). */
    @Transactional
    public int refreshForAttempt(UUID attemptId) {
        long startedAt = System.nanoTime();
        int upserts = recalculate(Set.of(attemptId), false);
        metrics.refreshSucceeded(Duration.ofNanos(System.nanoTime() - startedAt));
        return upserts;
    }

    /** Bounded incremental refresh scoped to a set of impacted attempts. */
    @Transactional
    public int refreshForAttempts(Collection<UUID> attemptIds) {
        long startedAt = System.nanoTime();
        int upserts = recalculate(Set.copyOf(attemptIds), false);
        metrics.refreshSucceeded(Duration.ofNanos(System.nanoTime() - startedAt));
        return upserts;
    }

    private int recalculate(Set<UUID> impactedAttemptIds, boolean full) {
        Instant now = Instant.now();
        List<AttemptAnalyticsCatalog.AttemptRow> impacted = delivery.attemptsByIds(impactedAttemptIds);
        if (!full && impacted.isEmpty()) {
            return 0;
        }

        Set<UUID> affectedUsers = impacted.stream().map(AttemptAnalyticsCatalog.AttemptRow::userId)
                .collect(Collectors.toSet());
        Set<UUID> affectedVersions = impacted.stream()
                .map(AttemptAnalyticsCatalog.AttemptRow::assessmentVersionId).collect(Collectors.toSet());

        // Working set: for a rebuild every completed attempt; otherwise the affected users' attempts
        // and the affected assessment versions' attempts, deduplicated.
        Map<UUID, AttemptAnalyticsCatalog.AttemptRow> working = new LinkedHashMap<>();
        if (full) {
            delivery.completedAttempts().forEach(row -> working.putIfAbsent(row.attemptId(), row));
        } else {
            delivery.attemptsForUsers(affectedUsers).forEach(row -> working.putIfAbsent(row.attemptId(), row));
            delivery.attemptsForVersions(affectedVersions).forEach(row -> working.putIfAbsent(row.attemptId(), row));
        }
        if (working.isEmpty()) {
            upsertOrganizations(now);
            return 0;
        }

        // Qualification: an attempt is included when its current Result is on record (§15, §16).
        Map<UUID, AnalyticsOutcomeCatalog.ScoreRow> scores = outcomes.currentScores(working.keySet()).stream()
                .collect(Collectors.toMap(AnalyticsOutcomeCatalog.ScoreRow::attemptId, Function.identity(),
                        (a, b) -> a));
        List<AttemptAnalyticsCatalog.AttemptRow> qualified = working.values().stream()
                .filter(row -> scores.containsKey(row.attemptId()))
                .toList();
        Map<UUID, java.math.BigDecimal> scoreByAttempt = scores.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().obtainedMarks(),
                        (a, b) -> a));

        List<AnalyticsFact> allFacts = factBuilder.build(qualified, scoreByAttempt);
        List<AnalyticsFact> scopeFacts = full ? allFacts
                : allFacts.stream().filter(fact -> affectedVersions.contains(fact.assessmentVersionId())).toList();

        int upserts = 0;
        upserts += upsertStudents(full, affectedUsers, allFacts, now);
        upserts += upsertAssessments(scopeFacts, now);
        upserts += upsertQuestions(scopeFacts, now);
        upserts += upsertTopics(scopeFacts, now);
        upserts += upsertOrganizations(now);
        evictCaches(affectedUsers, allFacts, scopeFacts);
        return upserts;
    }

    /** Cached aggregates may become stale when a new Result is authoritative ({@code §63}). */
    private void evictCaches(Set<UUID> affectedUsers, List<AnalyticsFact> allFacts, List<AnalyticsFact> scopeFacts) {
        for (UUID userId : affectedUsers) {
            cache.evict(AnalyticsCacheKeys.student(userId));
        }
        scopeFacts.stream().filter(fact -> fact.assessmentId() != null).map(AnalyticsFact::assessmentId).distinct()
                .forEach(assessmentId -> cache.evict(AnalyticsCacheKeys.assessment(assessmentId)));
        for (AnalyticsFact fact : allFacts) {
            for (AnalyticsFact.QuestionFact question : fact.questions()) {
                if (question.questionId() != null) {
                    cache.evict(AnalyticsCacheKeys.question(question.questionId()));
                }
                if (question.topicId() != null) {
                    cache.evict(AnalyticsCacheKeys.topic(question.topicId()));
                }
            }
        }
        identity.organizationIds().forEach(organizationId -> cache.evict(AnalyticsCacheKeys.organization(organizationId)));
    }

    private int upsertStudents(boolean full, Set<UUID> affectedUsers, List<AnalyticsFact> allFacts, Instant now) {
        Map<UUID, List<AnalyticsFact>> byUser = allFacts.stream()
                .collect(Collectors.groupingBy(AnalyticsFact::userId));
        Set<UUID> scope = full ? new HashSet<>(byUser.keySet()) : new HashSet<>(affectedUsers);
        int upserts = 0;
        for (UUID userId : scope) {
            StudentAnalytics computed = calculator.student(userId, byUser.getOrDefault(userId, List.of()), now);
            StudentAnalytics row = studentRepository.findByUserId(userId).orElseGet(StudentAnalytics::new);
            if (row.getUserId() == null) {
                row.setUserId(userId);
            }
            row.setAssessmentsAttempted(computed.getAssessmentsAttempted());
            row.setAverageScore(computed.getAverageScore());
            row.setAccuracy(computed.getAccuracy());
            row.setAverageTime(computed.getAverageTime());
            row.setLastCalculatedOn(now);
            studentRepository.save(row);
            upserts++;
        }
        return upserts;
    }

    private int upsertAssessments(List<AnalyticsFact> scopeFacts, Instant now) {
        Map<UUID, List<AnalyticsFact>> byAssessment = scopeFacts.stream()
                .filter(fact -> fact.assessmentId() != null)
                .collect(Collectors.groupingBy(AnalyticsFact::assessmentId));
        int upserts = 0;
        for (Map.Entry<UUID, List<AnalyticsFact>> entry : byAssessment.entrySet()) {
            AssessmentAnalytics computed = calculator.assessment(entry.getKey(), entry.getValue(), now);
            AssessmentAnalytics row = assessmentRepository.findByAssessmentId(entry.getKey())
                    .orElseGet(AssessmentAnalytics::new);
            if (row.getAssessmentId() == null) {
                row.setAssessmentId(entry.getKey());
            }
            row.setTotalAttempts(computed.getTotalAttempts());
            row.setAverageScore(computed.getAverageScore());
            row.setHighestScore(computed.getHighestScore());
            row.setLowestScore(computed.getLowestScore());
            row.setLastCalculatedOn(now);
            assessmentRepository.save(row);
            upserts++;
        }
        return upserts;
    }

    private int upsertQuestions(List<AnalyticsFact> scopeFacts, Instant now) {
        Map<UUID, List<AnalyticsFact.QuestionFact>> byQuestion = groupByQuestion(scopeFacts);
        int upserts = 0;
        for (Map.Entry<UUID, List<AnalyticsFact.QuestionFact>> entry : byQuestion.entrySet()) {
            QuestionAnalytics computed = calculator.question(entry.getKey(), entry.getValue(), now);
            QuestionAnalytics row = questionRepository.findByQuestionId(entry.getKey())
                    .orElseGet(QuestionAnalytics::new);
            if (row.getQuestionId() == null) {
                row.setQuestionId(entry.getKey());
            }
            row.setTotalAttempts(computed.getTotalAttempts());
            row.setCorrectAttempts(computed.getCorrectAttempts());
            row.setIncorrectAttempts(computed.getIncorrectAttempts());
            row.setAverageTime(computed.getAverageTime());
            row.setDifficultyIndex(computed.getDifficultyIndex());
            row.setLastCalculatedOn(now);
            questionRepository.save(row);
            upserts++;
        }
        return upserts;
    }

    private int upsertTopics(List<AnalyticsFact> scopeFacts, Instant now) {
        Map<UUID, List<AnalyticsFact.QuestionFact>> byTopic = groupByTopic(scopeFacts);
        int upserts = 0;
        for (Map.Entry<UUID, List<AnalyticsFact.QuestionFact>> entry : byTopic.entrySet()) {
            TopicAnalytics computed = calculator.topic(entry.getKey(), entry.getValue(), now);
            TopicAnalytics row = topicRepository.findByTopicId(entry.getKey()).orElseGet(TopicAnalytics::new);
            if (row.getTopicId() == null) {
                row.setTopicId(entry.getKey());
            }
            row.setTotalAttempts(computed.getTotalAttempts());
            row.setAverageScore(computed.getAverageScore());
            row.setAccuracy(computed.getAccuracy());
            row.setLastCalculatedOn(now);
            topicRepository.save(row);
            upserts++;
        }
        return upserts;
    }

    private Map<UUID, List<AnalyticsFact.QuestionFact>> groupByQuestion(List<AnalyticsFact> facts) {
        Map<UUID, List<AnalyticsFact.QuestionFact>> grouped = new HashMap<>();
        for (AnalyticsFact fact : facts) {
            for (AnalyticsFact.QuestionFact question : fact.questions()) {
                if (question.questionId() != null) {
                    grouped.computeIfAbsent(question.questionId(), key -> new ArrayList<>()).add(question);
                }
            }
        }
        return grouped;
    }

    private Map<UUID, List<AnalyticsFact.QuestionFact>> groupByTopic(List<AnalyticsFact> facts) {
        Map<UUID, List<AnalyticsFact.QuestionFact>> grouped = new HashMap<>();
        for (AnalyticsFact fact : facts) {
            for (AnalyticsFact.QuestionFact question : fact.questions()) {
                if (question.topicId() != null) {
                    grouped.computeIfAbsent(question.topicId(), key -> new ArrayList<>()).add(question);
                }
            }
        }
        return grouped;
    }

    private int upsertOrganizations(Instant now) {
        int totalStudents = identity.activeStudentUserIds().size();
        int totalAssessments = assessment.qualifyingAssessmentIds().size();
        List<UUID> completedIds = delivery.completedAttempts().stream()
                .map(AttemptAnalyticsCatalog.AttemptRow::attemptId).toList();
        int totalAttempts = outcomes.currentScores(completedIds).size();
        Instant windowStart = now.minus(properties.getActiveUserWindow());
        int activeUsers = delivery.activeUserIdsSince(windowStart).size();
        int upserts = 0;
        for (UUID organizationId : identity.organizationIds()) {
            OrganizationAnalytics computed = calculator.organization(organizationId, totalStudents,
                    totalAssessments, totalAttempts, activeUsers, now);
            OrganizationAnalytics row = organizationRepository.findByOrganizationId(organizationId)
                    .orElseGet(OrganizationAnalytics::new);
            if (row.getOrganizationId() == null) {
                row.setOrganizationId(organizationId);
            }
            row.setTotalStudents(computed.getTotalStudents());
            row.setTotalAssessments(computed.getTotalAssessments());
            row.setTotalAttempts(computed.getTotalAttempts());
            row.setActiveUsers(computed.getActiveUsers());
            row.setLastCalculatedOn(now);
            organizationRepository.save(row);
            upserts++;
        }
        return upserts;
    }
}
