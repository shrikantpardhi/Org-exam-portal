package com.company.exam.delivery.service;

import com.company.exam.delivery.api.AttemptAnalyticsCatalog;
import com.company.exam.delivery.entity.AttemptStatus;
import com.company.exam.delivery.entity.StudentAttempt;
import com.company.exam.delivery.repository.AttemptQuestionRepository;
import com.company.exam.delivery.repository.StudentAttemptRepository;
import com.company.exam.delivery.repository.StudentResponseRepository;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Default {@link AttemptAnalyticsCatalog} ({@code specs/17-P8-Analytics-V1.5.md §12, §13, §44, §45}). */
@Service
public class AttemptAnalyticsCatalogService implements AttemptAnalyticsCatalog {

    private final StudentAttemptRepository attemptRepository;
    private final AttemptQuestionRepository questionRepository;
    private final StudentResponseRepository responseRepository;

    public AttemptAnalyticsCatalogService(StudentAttemptRepository attemptRepository,
            AttemptQuestionRepository questionRepository, StudentResponseRepository responseRepository) {
        this.attemptRepository = attemptRepository;
        this.questionRepository = questionRepository;
        this.responseRepository = responseRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AttemptRow> completedAttempts() {
        return attemptRepository.findByAttemptStatusIn(List.of(AttemptStatus.SUBMITTED, AttemptStatus.EVALUATED))
                .stream().map(this::toRow).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AttemptRow> attemptsForUsers(Collection<UUID> userIds) {
        return userIds.isEmpty() ? List.of()
                : attemptRepository.findByUserIdIn(userIds).stream().map(this::toRow).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AttemptRow> attemptsForVersions(Collection<UUID> assessmentVersionIds) {
        return assessmentVersionIds.isEmpty() ? List.of()
                : attemptRepository.findByAssessmentVersionIdIn(assessmentVersionIds).stream().map(this::toRow).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AttemptRow> attemptsByIds(Collection<UUID> attemptIds) {
        return attemptIds.isEmpty() ? List.of()
                : attemptRepository.findAllById(attemptIds).stream().map(this::toRow).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<SnapshotRow> snapshots(Collection<UUID> attemptIds) {
        if (attemptIds.isEmpty()) {
            return List.of();
        }
        return questionRepository.findByAttemptIdIn(attemptIds).stream()
                .map(question -> new SnapshotRow(question.getAttemptId(), question.getQuestionVersionId(),
                        question.getSectionName(), question.getMarks()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ResponseRow> responses(Collection<UUID> attemptIds) {
        if (attemptIds.isEmpty()) {
            return List.of();
        }
        return responseRepository.findByAttemptIdIn(attemptIds).stream()
                .map(response -> new ResponseRow(response.getAttemptId(), response.getQuestionVersionId(),
                        response.getTimeSpentSeconds(),
                        response.getResponse() != null && !response.getResponse().isBlank()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> activeUserIdsSince(Instant since) {
        return attemptRepository.findActiveUserIdsSince(since);
    }

    @Override
    @Transactional(readOnly = true)
    public List<QuestionPopulation> questionPopulation(Collection<UUID> questionVersionIds) {
        if (questionVersionIds.isEmpty()) {
            return List.of();
        }
        java.util.Map<UUID, Long> answered = new java.util.HashMap<>();
        for (Object[] row : responseRepository.countAnsweredByQuestionVersionIds(questionVersionIds)) {
            answered.put((UUID) row[0], ((Number) row[1]).longValue());
        }
        return questionRepository.countByQuestionVersionIds(questionVersionIds).stream()
                .map(row -> {
                    UUID questionVersionId = (UUID) row[0];
                    long snapshots = ((Number) row[1]).longValue();
                    return new QuestionPopulation(questionVersionId, snapshots,
                            answered.getOrDefault(questionVersionId, 0L));
                })
                .toList();
    }

    private AttemptRow toRow(StudentAttempt attempt) {
        return new AttemptRow(attempt.getId(), attempt.getUserId(), attempt.getAssessmentVersionId(),
                attempt.getAttemptMode() == null ? null : attempt.getAttemptMode().name(),
                attempt.getAttemptStatus() == null ? null : attempt.getAttemptStatus().name(),
                attempt.getStartedOn());
    }
}
