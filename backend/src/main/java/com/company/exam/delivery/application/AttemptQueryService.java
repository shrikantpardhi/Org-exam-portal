package com.company.exam.delivery.application;

import com.company.exam.assessment.api.AssessmentDefinitionCatalog;
import com.company.exam.common.exception.InvalidStateException;
import com.company.exam.common.exception.NotFoundException;
import com.company.exam.content.api.ContentCatalog;
import com.company.exam.delivery.engine.DeliveryConfiguration;
import com.company.exam.delivery.entity.AttemptQuestion;
import com.company.exam.delivery.entity.AttemptQuestionOption;
import com.company.exam.delivery.entity.AttemptStatus;
import com.company.exam.delivery.entity.StudentAttempt;
import com.company.exam.delivery.entity.StudentResponse;
import com.company.exam.delivery.repository.AttemptQuestionOptionRepository;
import com.company.exam.delivery.repository.AttemptQuestionRepository;
import com.company.exam.delivery.repository.StudentResponseRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only runtime queries built from the prepared snapshot, never from the live assessment
 * definition ({@code specs/14-P5-Delivery-V1.5.md §38-§41, §85}).
 *
 * <p>Question payloads never include {@code isCorrect}, answer keys, review metadata, author
 * identity, or asset file paths.
 */
@Service
public class AttemptQueryService {

    private final AttemptEligibilityService eligibilityService;
    private final AttemptQuestionRepository questionRepository;
    private final AttemptQuestionOptionRepository optionRepository;
    private final StudentResponseRepository responseRepository;
    private final com.company.exam.delivery.repository.StudentAttemptRepository attemptRepository;
    private final AssessmentDefinitionCatalog assessmentCatalog;
    private final ContentCatalog contentCatalog;
    private final RuntimeConfigurationService runtimeConfigurationService;

    public AttemptQueryService(AttemptEligibilityService eligibilityService,
            AttemptQuestionRepository questionRepository, AttemptQuestionOptionRepository optionRepository,
            StudentResponseRepository responseRepository,
            com.company.exam.delivery.repository.StudentAttemptRepository attemptRepository,
            AssessmentDefinitionCatalog assessmentCatalog, ContentCatalog contentCatalog,
            RuntimeConfigurationService runtimeConfigurationService) {
        this.eligibilityService = eligibilityService;
        this.questionRepository = questionRepository;
        this.optionRepository = optionRepository;
        this.responseRepository = responseRepository;
        this.attemptRepository = attemptRepository;
        this.assessmentCatalog = assessmentCatalog;
        this.contentCatalog = contentCatalog;
        this.runtimeConfigurationService = runtimeConfigurationService;
    }

    @Transactional(readOnly = true)
    public DeliveryDtos.AttemptResponse getAttempt(UUID attemptId, UUID userId) {
        return describe(eligibilityService.requireOwned(attemptId, userId));
    }

    /**
     * Attempt history for a student ({@code §69}). Students see only their own attempts; staff
     * visibility is permission-controlled at the controller. Filters: assessmentVersionId, status,
     * from, to.
     */
    @Transactional(readOnly = true)
    public List<DeliveryDtos.AttemptResponse> list(UUID userId, UUID assessmentVersionId, String status, Instant from,
            Instant to) {
        return attemptRepository.findByUserIdOrderByStartedOnDesc(userId).stream()
                .filter(attempt -> assessmentVersionId == null
                        || assessmentVersionId.equals(attempt.getAssessmentVersionId()))
                .filter(attempt -> status == null || (attempt.getAttemptStatus() != null
                        && status.equals(attempt.getAttemptStatus().name())))
                .filter(attempt -> from == null
                        || (attempt.getStartedOn() != null && !attempt.getStartedOn().isBefore(from)))
                .filter(attempt -> to == null
                        || (attempt.getStartedOn() != null && !attempt.getStartedOn().isAfter(to)))
                .map(this::describe)
                .toList();
    }

    private DeliveryDtos.AttemptResponse describe(StudentAttempt attempt) {
        UUID attemptId = attempt.getId();
        AssessmentDefinitionCatalog.ScheduleView schedule = assessmentCatalog
                .requireSchedule(attempt.getAssessmentScheduleId());
        AssessmentDefinitionCatalog.PublishedAssessmentVersion version = assessmentCatalog
                .requirePublishedVersion(attempt.getAssessmentVersionId());
        List<String> sections = questionRepository.findDistinctSectionNames(attemptId);
        DeliveryConfiguration configuration = runtimeConfigurationService.resolve(schedule, version, sections);
        return new DeliveryDtos.AttemptResponse(attempt.getId(), attempt.getAssessmentVersionId(),
                attempt.getAssessmentScheduleId(),
                attempt.getAttemptMode() == null ? null : attempt.getAttemptMode().name(),
                attempt.getAttemptStatus() == null ? null : attempt.getAttemptStatus().name(),
                attempt.getSnapshotStatus() == null ? null : attempt.getSnapshotStatus().name(),
                attempt.getStartedOn(), attempt.getSubmittedOn(), Instant.now(),
                DeliveryTimers.timer(attempt, configuration), configuration, progress(attemptId));
    }

    @Transactional(readOnly = true)
    public List<DeliveryDtos.AttemptQuestionView> questions(UUID attemptId, UUID userId) {
        StudentAttempt attempt = eligibilityService.requireOwned(attemptId, userId);
        requireActiveAttempt(attempt);
        List<AttemptQuestion> snapshot = questionRepository.findByAttemptIdOrderByDisplayOrderAsc(attemptId);
        Map<UUID, List<AttemptQuestionOption>> optionsByQuestion = new HashMap<>();
        if (!snapshot.isEmpty()) {
            List<UUID> ids = snapshot.stream().map(AttemptQuestion::getId).toList();
            for (AttemptQuestionOption option : optionRepository.findByAttemptQuestionIdInOrderByDisplayOrderAsc(ids)) {
                optionsByQuestion.computeIfAbsent(option.getAttemptQuestionId(), key -> new ArrayList<>()).add(option);
            }
        }
        List<DeliveryDtos.AttemptQuestionView> result = new ArrayList<>(snapshot.size());
        for (AttemptQuestion row : snapshot) {
            result.add(toView(row, optionsByQuestion.getOrDefault(row.getId(), List.of())));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public DeliveryDtos.AttemptQuestionView question(UUID attemptId, UUID questionVersionId, UUID userId) {
        StudentAttempt attempt = eligibilityService.requireOwned(attemptId, userId);
        requireActiveAttempt(attempt);
        AttemptQuestion row = questionRepository.findByAttemptIdAndQuestionVersionId(attemptId, questionVersionId)
                .orElseThrow(() -> NotFoundException.of("Attempt question", questionVersionId));
        List<AttemptQuestionOption> options = optionRepository
                .findByAttemptQuestionIdOrderByDisplayOrderAsc(row.getId());
        return toView(row, options);
    }

    private void requireActiveAttempt(StudentAttempt attempt) {
        if (attempt.getAttemptStatus() == AttemptStatus.SUBMITTED
                || attempt.getAttemptStatus() == AttemptStatus.EVALUATED) {
            throw new InvalidStateException("Attempt is no longer active");
        }
    }

    private DeliveryDtos.AttemptQuestionView toView(AttemptQuestion row, List<AttemptQuestionOption> snapshotOptions) {
        ContentCatalog.RuntimeQuestion content = contentCatalog.runtimeQuestion(row.getQuestionVersionId());
        Map<UUID, ContentCatalog.RuntimeOption> byOptionId = new HashMap<>();
        for (ContentCatalog.RuntimeOption option : content.options()) {
            byOptionId.put(option.optionId(), option);
        }
        List<DeliveryDtos.OptionView> options = new ArrayList<>(snapshotOptions.size());
        for (AttemptQuestionOption snapshotOption : snapshotOptions) {
            ContentCatalog.RuntimeOption resolved = byOptionId.get(snapshotOption.getQuestionOptionId());
            if (resolved == null) {
                continue;
            }
            options.add(new DeliveryDtos.OptionView(resolved.optionId(), resolved.optionKey(),
                    snapshotOption.getDisplayOrder(), resolved.content(), resolved.renderedContent()));
        }
        return new DeliveryDtos.AttemptQuestionView(row.getId(), row.getQuestionVersionId(), row.getDisplayOrder(),
                row.getSectionName(), row.getMarks(), row.getNegativeMarks(), content.requiresLatexParsing(),
                content.contentFormat(), content.content(), content.renderedContent(), options);
    }

    private DeliveryDtos.ProgressSummary progress(UUID attemptId) {
        long total = questionRepository.countByAttemptId(attemptId);
        List<StudentResponse> responses = responseRepository.findByAttemptId(attemptId);
        long answered = responses.stream()
                .filter(response -> response.getResponse() != null && !response.getResponse().isBlank())
                .count();
        long visited = responses.stream().filter(response -> Boolean.TRUE.equals(response.getVisited())).count();
        long marked = responses.stream()
                .filter(response -> Boolean.TRUE.equals(response.getMarkedForReview()))
                .count();
        return new DeliveryDtos.ProgressSummary(total, answered, visited, marked);
    }
}
