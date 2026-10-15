package com.company.exam.analytics.service;

import com.company.exam.analytics.dto.AnalyticsDtos;
import com.company.exam.analytics.observability.AnalyticsMetrics;
import com.company.exam.analytics.repository.AssessmentAnalyticsRepository;
import com.company.exam.analytics.repository.OrganizationAnalyticsRepository;
import com.company.exam.analytics.repository.QuestionAnalyticsRepository;
import com.company.exam.analytics.repository.StudentAnalyticsRepository;
import com.company.exam.analytics.repository.TopicAnalyticsRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Analytics data-quality checks and drift detection
 * ({@code specs/17-P8-Analytics-V1.5.md §113-§117}).
 *
 * <p>Read-only. It flags negative counts, impossible counts relative to source, values outside the
 * approved bounds, and inconsistent score ranges. These are data-quality signals, not user-facing
 * judgments ({@code §114}).
 */
@Service
public class AnalyticsDataQualityService {

    private final StudentAnalyticsRepository studentRepository;
    private final AssessmentAnalyticsRepository assessmentRepository;
    private final QuestionAnalyticsRepository questionRepository;
    private final TopicAnalyticsRepository topicRepository;
    private final OrganizationAnalyticsRepository organizationRepository;
    private final AnalyticsMetrics metrics;

    public AnalyticsDataQualityService(StudentAnalyticsRepository studentRepository,
            AssessmentAnalyticsRepository assessmentRepository, QuestionAnalyticsRepository questionRepository,
            TopicAnalyticsRepository topicRepository, OrganizationAnalyticsRepository organizationRepository,
            AnalyticsMetrics metrics) {
        this.studentRepository = studentRepository;
        this.assessmentRepository = assessmentRepository;
        this.questionRepository = questionRepository;
        this.topicRepository = topicRepository;
        this.organizationRepository = organizationRepository;
        this.metrics = metrics;
    }

    /** Runs every check and returns the findings ({@code §113}). */
    @Transactional(readOnly = true)
    public AnalyticsDtos.DataQualityReport validate() {
        List<AnalyticsDtos.DataQualityFinding> findings = new ArrayList<>();

        studentRepository.findAll().forEach(row -> {
            String scope = "student:" + row.getUserId();
            if (row.getAssessmentsAttempted() != null && row.getAssessmentsAttempted() < 0) {
                findings.add(new AnalyticsDtos.DataQualityFinding("negative_count", scope,
                        "assessmentsAttempted=" + row.getAssessmentsAttempted()));
            }
            if (outOfBoundsPercentage(row.getAccuracy())) {
                findings.add(new AnalyticsDtos.DataQualityFinding("accuracy_bounds", scope,
                        "accuracy=" + row.getAccuracy()));
            }
            if (negativeTime(row.getAverageTime())) {
                findings.add(new AnalyticsDtos.DataQualityFinding("time_bounds", scope,
                        "averageTime=" + row.getAverageTime()));
            }
        });

        assessmentRepository.findAll().forEach(row -> {
            String scope = "assessment:" + row.getAssessmentId();
            if (row.getTotalAttempts() != null && row.getTotalAttempts() < 0) {
                findings.add(new AnalyticsDtos.DataQualityFinding("negative_count", scope,
                        "totalAttempts=" + row.getTotalAttempts()));
            }
            if (row.getHighestScore() != null && row.getLowestScore() != null
                    && row.getHighestScore().compareTo(row.getLowestScore()) < 0) {
                findings.add(new AnalyticsDtos.DataQualityFinding("score_range", scope,
                        "highest < lowest"));
            }
            if (row.getAverageScore() != null && row.getLowestScore() != null && row.getHighestScore() != null
                    && (row.getAverageScore().compareTo(row.getLowestScore()) < 0
                            || row.getAverageScore().compareTo(row.getHighestScore()) > 0)) {
                findings.add(new AnalyticsDtos.DataQualityFinding("score_bounds", scope,
                        "average outside [lowest, highest]"));
            }
        });

        questionRepository.findAll().forEach(row -> {
            String scope = "question:" + row.getQuestionId();
            int correct = row.getCorrectAttempts() == null ? 0 : row.getCorrectAttempts();
            int incorrect = row.getIncorrectAttempts() == null ? 0 : row.getIncorrectAttempts();
            int total = row.getTotalAttempts() == null ? 0 : row.getTotalAttempts();
            if (correct < 0 || incorrect < 0 || total < 0) {
                findings.add(new AnalyticsDtos.DataQualityFinding("negative_count", scope, "counts negative"));
            }
            if (correct + incorrect != total) {
                findings.add(new AnalyticsDtos.DataQualityFinding("count_mismatch", scope,
                        "correct(" + correct + ")+incorrect(" + incorrect + ") != total(" + total + ")"));
            }
            if (row.getDifficultyIndex() != null
                    && (row.getDifficultyIndex().signum() < 0
                            || row.getDifficultyIndex().compareTo(BigDecimal.ONE) > 0)) {
                findings.add(new AnalyticsDtos.DataQualityFinding("difficulty_bounds", scope,
                        "difficultyIndex=" + row.getDifficultyIndex()));
            }
            if (negativeTime(row.getAverageTime())) {
                findings.add(new AnalyticsDtos.DataQualityFinding("time_bounds", scope,
                        "averageTime=" + row.getAverageTime()));
            }
        });

        topicRepository.findAll().forEach(row ->
                checkCountAndAccuracy("topic:" + row.getTopicId(), row.getTotalAttempts(), row.getAccuracy(),
                        findings));

        organizationRepository.findAll().forEach(row -> {
            String scope = "organization:" + row.getOrganizationId();
            if (negative(row.getTotalStudents()) || negative(row.getTotalAssessments())
                    || negative(row.getTotalAttempts()) || negative(row.getActiveUsers())) {
                findings.add(new AnalyticsDtos.DataQualityFinding("negative_count", scope, "counts negative"));
            }
            if (row.getActiveUsers() != null && row.getTotalStudents() != null
                    && row.getActiveUsers() > row.getTotalStudents()) {
                findings.add(new AnalyticsDtos.DataQualityFinding("count_impossible", scope,
                        "activeUsers > totalStudents"));
            }
        });

        metrics.dataQualityFailures(findings.size());
        if (findings.size() > 0) {
            metrics.alert("data_quality");
        }
        return new AnalyticsDtos.DataQualityReport(findings.size(), findings);
    }

    private void checkCountAndAccuracy(String scope, Integer totalAttempts, BigDecimal accuracy,
            List<AnalyticsDtos.DataQualityFinding> findings) {
        if (negative(totalAttempts)) {
            findings.add(new AnalyticsDtos.DataQualityFinding("negative_count", scope,
                    "totalAttempts=" + totalAttempts));
        }
        if (outOfBoundsPercentage(accuracy)) {
            findings.add(new AnalyticsDtos.DataQualityFinding("accuracy_bounds", scope, "accuracy=" + accuracy));
        }
    }

    private boolean outOfBoundsPercentage(BigDecimal value) {
        return value != null && (value.signum() < 0 || value.compareTo(new BigDecimal("100")) > 0);
    }

    private boolean negativeTime(BigDecimal value) {
        return value != null && value.signum() < 0;
    }

    private boolean negative(Integer value) {
        return value != null && value < 0;
    }
}
