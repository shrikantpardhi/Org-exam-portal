package com.company.exam.delivery.service;

import com.company.exam.common.exception.NotFoundException;
import com.company.exam.delivery.api.AttemptEvaluationCatalog;
import com.company.exam.delivery.entity.StudentAttempt;
import com.company.exam.delivery.repository.AttemptQuestionRepository;
import com.company.exam.delivery.repository.StudentAttemptRepository;
import com.company.exam.delivery.repository.StudentResponseRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Default {@link AttemptEvaluationCatalog} ({@code specs/15-P6-Evaluation-V1.5.md §64, §65}). */
@Service
public class AttemptEvaluationCatalogService implements AttemptEvaluationCatalog {

    private final StudentAttemptRepository attemptRepository;
    private final AttemptQuestionRepository questionRepository;
    private final StudentResponseRepository responseRepository;

    public AttemptEvaluationCatalogService(StudentAttemptRepository attemptRepository,
            AttemptQuestionRepository questionRepository, StudentResponseRepository responseRepository) {
        this.attemptRepository = attemptRepository;
        this.questionRepository = questionRepository;
        this.responseRepository = responseRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public AttemptView requireAttempt(UUID attemptId) {
        StudentAttempt attempt = attemptRepository.findById(attemptId)
                .orElseThrow(() -> NotFoundException.of("Attempt", attemptId));
        return toView(attempt);
    }

    private AttemptView toView(StudentAttempt attempt) {
        return new AttemptView(attempt.getId(), attempt.getUserId(), attempt.getAssessmentVersionId(),
                attempt.getAssessmentScheduleId(),
                attempt.getAttemptStatus() == null ? null : attempt.getAttemptStatus().name(),
                attempt.getSnapshotStatus() == null ? null : attempt.getSnapshotStatus().name());
    }

    @Override
    @Transactional(readOnly = true)
    public List<AttemptView> attempts(java.util.Collection<UUID> attemptIds) {
        return attemptRepository.findAllById(attemptIds).stream().map(this::toView).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<SnapshotQuestionView> snapshot(UUID attemptId) {
        return questionRepository.findByAttemptIdOrderByDisplayOrderAsc(attemptId).stream()
                .map(question -> new SnapshotQuestionView(question.getQuestionVersionId(),
                        question.getDisplayOrder(), question.getSectionName(), question.getMarks(),
                        question.getNegativeMarks()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ResponseView> responses(UUID attemptId) {
        return responseRepository.findByAttemptId(attemptId).stream()
                .map(response -> new ResponseView(response.getQuestionVersionId(), response.getResponse()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> attemptIdsForUser(UUID userId) {
        return attemptRepository.findByUserIdOrderByStartedOnDesc(userId).stream()
                .map(StudentAttempt::getId)
                .toList();
    }
}
