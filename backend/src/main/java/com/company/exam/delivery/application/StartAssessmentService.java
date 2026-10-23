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
 *
 * <p>Authorization includes the paid-content entitlement gate
 * ({@code specs/14-P5-Delivery-V1.5.md §19}; ADR-0026): the assessment must be free content or covered
 * by a currently valid AccessGrant, decided server-side before the attempt transition.
 */
@Service
public class StartAssessmentService {

    private final StudentAttemptRepository attemptRepository;
    private final AttemptQuestionRepository questionRepository;
    private final AttemptEligibilityService eligibilityService;
    private final EntitlementAccessGate entitlementAccessGate;
    private final RuntimeConfigurationService runtimeConfigurationService;
    private final AssessmentSessionService sessionService;
    private final com.company.exam.delivery.observability.DeliveryMetrics metrics;

    public StartAssessmentService(StudentAttemptRepository attemptRepository,
            AttemptQuestionRepository questionRepository,
            AttemptEligibilityService eligibilityService, EntitlementAccessGate entitlementAccessGate,
            RuntimeConfigurationService runtimeConfigurationService, AssessmentSessionService sessionService,
            com.company.exam.delivery.observability.DeliveryMetrics metrics) {
        this.attemptRepository = attemptRepository;
        this.questionRepository = questionRepository;
        this.eligibilityService = eligibilityService;
        this.entitlementAccessGate = entitlementAccessGate;
        this.runtimeConfigurationService = runtimeConfigurationService;
        this.sessionService = sessionService;
        this.metrics = metrics;
    }

    @Transactional
    public DeliveryDtos.StartResponse start(UUID scheduleId, UUID userId) {
        return metrics.attemptStart(() -> startAttempt(scheduleId, userId));
    }

    private DeliveryDtos.StartResponse startAttempt(UUID scheduleId, UUID userId) {
        // Schedule and published version in one statement: both are needed on every Start and
        // neither can change during the launch (GAP-059).
        AssessmentDefinitionCatalog.ScheduleBinding binding =
                eligibilityService.requireOpenScheduleBinding(scheduleId);
        AssessmentDefinitionCatalog.ScheduleView schedule = binding.schedule();
        AssessmentDefinitionCatalog.PublishedAssessmentVersion version = binding.version();

        // The paid-content gate (P5 §19 "User has required access"; ADR-0026). It runs after schedule
        // validation and before the attempt transition, so a denied start mutates no attempt state.
        entitlementAccessGate.assertAccess(userId, version.assessmentId(), schedule.mode(), Instant.now());

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
        int transitioned = attemptRepository.markStarted(ready.getId(), AttemptStatus.NOT_STARTED,
                AttemptStatus.IN_PROGRESS, SnapshotStatus.READY, userId, now);
        StudentAttempt attempt;
        if (transitioned == 1) {
            // This call won the race, so the resulting state is known without re-reading it. The
            // entity is detached by markStarted's clearAutomatically, so this mutation is in-memory
            // only and never flushes (GAP-059).
            ready.setAttemptStatus(AttemptStatus.IN_PROGRESS);
            ready.setStartedOn(now);
            attempt = ready;
        } else {
            // A concurrent Start won: re-read once to report its established context, or fail if the
            // attempt is still not started (for example the snapshot is not READY).
            attempt = attemptRepository.findById(ready.getId()).orElseThrow();
            if (attempt.getAttemptStatus() != AttemptStatus.IN_PROGRESS) {
                throw new InvalidStateException("Attempt could not be started");
            }
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
