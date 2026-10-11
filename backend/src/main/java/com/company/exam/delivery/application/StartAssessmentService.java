package com.company.exam.delivery.application;

import com.company.exam.assessment.api.AssessmentDefinitionCatalog;
import com.company.exam.common.exception.InvalidStateException;
import com.company.exam.delivery.engine.DeliveryConfiguration;
import com.company.exam.delivery.entity.AttemptStatus;
import com.company.exam.delivery.entity.AssessmentSession;
import com.company.exam.delivery.entity.SnapshotStatus;
import com.company.exam.delivery.entity.StudentAttempt;
import com.company.exam.delivery.exception.PreparationNotReadyException;
import com.company.exam.delivery.repository.AttemptQuestionRepository;
import com.company.exam.delivery.repository.StudentAttemptRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Start Exam — the performance-critical launch path ({@code specs/14-P5-Delivery-V1.5.md §26-§29}).
 *
 * <p>It performs authentication, authorization, schedule resolution, candidate attempt resolution,
 * READY snapshot metadata read, engine and configuration resolution, the race-safe
 * {@code NOT_STARTED -> IN_PROGRESS} transition, and session activation. It NEVER inserts
 * AttemptQuestion or AttemptQuestionOption rows, randomizes questions, constructs a snapshot, calls
 * evaluation, or rebuilds the assessment definition.
 */
@Service
public class StartAssessmentService {

    private final StudentAttemptRepository attemptRepository;
    private final AttemptQuestionRepository questionRepository;
    private final AssessmentDefinitionCatalog assessmentCatalog;
    private final AttemptEligibilityService eligibilityService;
    private final RuntimeConfigurationService runtimeConfigurationService;
    private final AssessmentSessionService sessionService;
    private final com.company.exam.delivery.observability.DeliveryMetrics metrics;

    public StartAssessmentService(StudentAttemptRepository attemptRepository,
            AttemptQuestionRepository questionRepository, AssessmentDefinitionCatalog assessmentCatalog,
            AttemptEligibilityService eligibilityService, RuntimeConfigurationService runtimeConfigurationService,
            AssessmentSessionService sessionService,
            com.company.exam.delivery.observability.DeliveryMetrics metrics) {
        this.attemptRepository = attemptRepository;
        this.questionRepository = questionRepository;
        this.assessmentCatalog = assessmentCatalog;
        this.eligibilityService = eligibilityService;
        this.runtimeConfigurationService = runtimeConfigurationService;
        this.sessionService = sessionService;
        this.metrics = metrics;
    }

    @Transactional
    public DeliveryDtos.StartResponse start(UUID scheduleId, UUID userId) {
        AssessmentDefinitionCatalog.ScheduleView schedule = eligibilityService.requireOpenSchedule(scheduleId);
        AssessmentDefinitionCatalog.PublishedAssessmentVersion version = assessmentCatalog
                .requirePublishedVersion(schedule.assessmentVersionId());

        List<StudentAttempt> candidates = eligibilityService.attemptsFor(scheduleId, userId);
        if (candidates.isEmpty()) {
            throw new PreparationNotReadyException();
        }

        // A concurrent or repeated Start returns the established context (idempotency policy).
        StudentAttempt alreadyStarted = candidates.stream()
                .filter(attempt -> attempt.getAttemptStatus() == AttemptStatus.IN_PROGRESS)
                .findFirst()
                .orElse(null);
        if (alreadyStarted != null) {
            return established(alreadyStarted, schedule, version, userId);
        }
        boolean submitted = candidates.stream().anyMatch(attempt -> attempt.getAttemptStatus() == AttemptStatus.SUBMITTED
                || attempt.getAttemptStatus() == AttemptStatus.EVALUATED);
        if (submitted) {
            throw new InvalidStateException("Attempt is already submitted");
        }

        StudentAttempt ready = candidates.stream()
                .filter(attempt -> attempt.getSnapshotStatus() == SnapshotStatus.READY
                        && attempt.getAttemptStatus() == AttemptStatus.NOT_STARTED)
                .findFirst()
                .orElse(null);
        if (ready == null) {
            throw new PreparationNotReadyException();
        }

        Instant now = Instant.now();
        attemptRepository.markStarted(ready.getId(), AttemptStatus.NOT_STARTED, AttemptStatus.IN_PROGRESS,
                SnapshotStatus.READY, userId, now);
        StudentAttempt attempt = attemptRepository.findById(ready.getId()).orElseThrow();
        if (attempt.getAttemptStatus() != AttemptStatus.IN_PROGRESS) {
            throw new InvalidStateException("Attempt could not be started");
        }
        return established(attempt, schedule, version, userId);
    }

    private DeliveryDtos.StartResponse established(StudentAttempt attempt,
            AssessmentDefinitionCatalog.ScheduleView schedule,
            AssessmentDefinitionCatalog.PublishedAssessmentVersion version, UUID userId) {
        Instant now = Instant.now();
        List<String> sections = questionRepository.findDistinctSectionNames(attempt.getId());
        DeliveryConfiguration configuration = runtimeConfigurationService.resolve(schedule, version, sections);
        AssessmentSession session = sessionService.activateOrCreate(attempt, userId, now);
        metrics.attemptStarted(attempt.getAttemptMode() == null ? null : attempt.getAttemptMode().name());
        return new DeliveryDtos.StartResponse(session.getId(), attempt.getId(), attempt.getAssessmentVersionId(),
                attempt.getAttemptMode() == null ? null : attempt.getAttemptMode().name(), attempt.getStartedOn(),
                now, DeliveryTimers.timer(attempt, configuration), configuration);
    }
}
