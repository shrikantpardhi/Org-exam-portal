package com.company.exam.analytics.service;

import com.company.exam.analytics.api.ReportingAnalyticsCatalog;
import com.company.exam.analytics.entity.AssessmentAnalytics;
import com.company.exam.analytics.entity.QuestionAnalytics;
import com.company.exam.analytics.entity.StudentAnalytics;
import com.company.exam.analytics.entity.OrganizationAnalytics;
import com.company.exam.analytics.entity.TopicAnalytics;
import com.company.exam.analytics.repository.AssessmentAnalyticsRepository;
import com.company.exam.analytics.repository.OrganizationAnalyticsRepository;
import com.company.exam.analytics.repository.QuestionAnalyticsRepository;
import com.company.exam.analytics.repository.StudentAnalyticsRepository;
import com.company.exam.analytics.repository.TopicAnalyticsRepository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Default {@link ReportingAnalyticsCatalog}
 * ({@code specs/22-P13-Reporting-V1.5.md §10, §40, §41, §43, §46}).
 *
 * <p>Read-only projections over the frozen analytics aggregates. Collection arguments are guarded
 * against an empty {@code in} clause and never widened into a full-table scan ({@code §7.6}).
 */
@Service
public class ReportingAnalyticsCatalogService implements ReportingAnalyticsCatalog {

    private final StudentAnalyticsRepository studentRepository;
    private final TopicAnalyticsRepository topicRepository;
    private final QuestionAnalyticsRepository questionRepository;
    private final AssessmentAnalyticsRepository assessmentRepository;
    private final OrganizationAnalyticsRepository organizationRepository;

    public ReportingAnalyticsCatalogService(StudentAnalyticsRepository studentRepository,
            TopicAnalyticsRepository topicRepository, QuestionAnalyticsRepository questionRepository,
            AssessmentAnalyticsRepository assessmentRepository,
            OrganizationAnalyticsRepository organizationRepository) {
        this.studentRepository = studentRepository;
        this.topicRepository = topicRepository;
        this.questionRepository = questionRepository;
        this.assessmentRepository = assessmentRepository;
        this.organizationRepository = organizationRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StudentAnalyticsRow> student(UUID userId) {
        return userId == null ? Optional.empty()
                : studentRepository.findByUserId(userId).map(this::toRow);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TopicAnalyticsRow> topics(Collection<UUID> topicIds) {
        if (topicIds == null || topicIds.isEmpty()) {
            return List.of();
        }
        return topicRepository.findByTopicIdIn(List.copyOf(topicIds)).stream()
                .map(topic -> new TopicAnalyticsRow(topic.getTopicId(), topic.getTotalAttempts(),
                        topic.getAverageScore(), topic.getAccuracy(), topic.getLastCalculatedOn()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<QuestionAnalyticsRow> questions(Collection<UUID> questionIds) {
        if (questionIds == null || questionIds.isEmpty()) {
            return List.of();
        }
        return questionRepository.findByQuestionIdIn(List.copyOf(questionIds)).stream()
                .map(question -> new QuestionAnalyticsRow(question.getQuestionId(), question.getTotalAttempts(),
                        question.getCorrectAttempts(), question.getIncorrectAttempts(), question.getAverageTime(),
                        question.getDifficultyIndex(), question.getLastCalculatedOn()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AssessmentAnalyticsRow> assessments(Collection<UUID> assessmentIds) {
        if (assessmentIds == null || assessmentIds.isEmpty()) {
            return List.of();
        }
        return assessmentRepository.findByAssessmentIdIn(List.copyOf(assessmentIds)).stream()
                .map(this::toRow)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrganizationAnalyticsRow> organizations(Collection<UUID> organizationIds) {
        if (organizationIds == null || organizationIds.isEmpty()) {
            return List.of();
        }
        List<OrganizationAnalyticsRow> rows = new java.util.ArrayList<>();
        for (UUID organizationId : organizationIds) {
            organizationRepository.findByOrganizationId(organizationId)
                    .map(analytics -> new OrganizationAnalyticsRow(analytics.getOrganizationId(),
                            analytics.getTotalStudents(), analytics.getTotalAssessments(),
                            analytics.getTotalAttempts(), analytics.getActiveUsers(),
                            analytics.getLastCalculatedOn()))
                    .ifPresent(rows::add);
        }
        return List.copyOf(rows);
    }

    private StudentAnalyticsRow toRow(StudentAnalytics analytics) {        return new StudentAnalyticsRow(analytics.getUserId(), analytics.getAssessmentsAttempted(),
                analytics.getAverageScore(), analytics.getAccuracy(), analytics.getAverageTime(),
                analytics.getLastCalculatedOn());
    }

    private AssessmentAnalyticsRow toRow(AssessmentAnalytics analytics) {
        return new AssessmentAnalyticsRow(analytics.getAssessmentId(), analytics.getTotalAttempts(),
                analytics.getAverageScore(), analytics.getHighestScore(), analytics.getLowestScore(),
                analytics.getLastCalculatedOn());
    }
}
