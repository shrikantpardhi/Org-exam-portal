package com.company.exam.delivery.application;

import com.company.exam.assessment.api.AssessmentDefinitionCatalog;
import com.company.exam.common.exception.InvalidStateException;
import com.company.exam.common.exception.NotFoundException;
import com.company.exam.delivery.engine.DeliveryConfiguration;
import com.company.exam.delivery.entity.AssessmentSession;
import com.company.exam.delivery.entity.AssessmentSessionStatus;
import com.company.exam.delivery.entity.StudentAttempt;
import com.company.exam.delivery.repository.AssessmentSessionRepository;
import com.company.exam.delivery.repository.AttemptQuestionRepository;
import com.company.exam.delivery.repository.StudentAttemptRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Runtime session lifecycle and recovery ({@code specs/14-P5-Delivery-V1.5.md §48-§53}).
 *
 * <p>A session is a lightweight runtime record separate from the attempt. Recovery reads persisted
 * PostgreSQL state and never reconstructs the snapshot, resets {@code started_on}, or restarts the
 * timer.
 */
@Service
public class AssessmentSessionService {

    private final AssessmentSessionRepository sessionRepository;
    private final StudentAttemptRepository attemptRepository;
    private final AttemptQuestionRepository questionRepository;
    private final AssessmentDefinitionCatalog assessmentCatalog;
    private final RuntimeConfigurationService runtimeConfigurationService;
    private final com.company.exam.delivery.observability.DeliveryMetrics metrics;

    public AssessmentSessionService(AssessmentSessionRepository sessionRepository,
            StudentAttemptRepository attemptRepository, AttemptQuestionRepository questionRepository,
            AssessmentDefinitionCatalog assessmentCatalog, RuntimeConfigurationService runtimeConfigurationService,
            com.company.exam.delivery.observability.DeliveryMetrics metrics) {
        this.sessionRepository = sessionRepository;
        this.attemptRepository = attemptRepository;
        this.questionRepository = questionRepository;
        this.assessmentCatalog = assessmentCatalog;
        this.runtimeConfigurationService = runtimeConfigurationService;
        this.metrics = metrics;
    }

    /** Creates a session or activates the existing ACTIVE session for an attempt. */
    @Transactional
    public AssessmentSession activateOrCreate(StudentAttempt attempt, UUID userId, Instant now) {
        return sessionRepository
                .findFirstByAttemptIdAndSessionStatusOrderByStartedOnDesc(attempt.getId(),
                        AssessmentSessionStatus.ACTIVE)
                .orElseGet(() -> {
                    AssessmentSession session = new AssessmentSession();
                    session.setUserId(userId);
                    session.setAssessmentScheduleId(attempt.getAssessmentScheduleId());
                    session.setAttemptId(attempt.getId());
                    session.setSessionStatus(AssessmentSessionStatus.ACTIVE);
                    session.setStartedOn(now);
                    session.setLastActivityOn(now);
                    return sessionRepository.save(session);
                });
    }

    /** The currently recoverable session for the authenticated student ({@code §49}). */
    @Transactional(readOnly = true)
    public DeliveryDtos.SessionResponse getActive(UUID userId) {
        AssessmentSession session = sessionRepository
                .findByUserIdAndSessionStatusOrderByLastActivityOnDesc(userId, AssessmentSessionStatus.ACTIVE)
                .stream()
                .findFirst()
                .orElseThrow(() -> new NotFoundException("No active assessment session"));
        return describe(session);
    }

    @Transactional(readOnly = true)
    public DeliveryDtos.SessionResponse get(UUID sessionId, UUID userId) {
        return describe(requireOwned(sessionId, userId));
    }

    /**
     * Recovery: authenticate, verify ownership, verify the session is still recoverable, verify the
     * attempt state, re-resolve the normalized configuration, and return the current runtime context
     * ({@code §51}). It never rebuilds the snapshot or restarts the timer.
     */
    @Transactional
    public DeliveryDtos.SessionResponse recover(UUID sessionId, UUID userId) {
        return metrics.sessionRecovery(() -> recoverSession(sessionId, userId));
    }

    private DeliveryDtos.SessionResponse recoverSession(UUID sessionId, UUID userId) {
        AssessmentSession session = requireOwned(sessionId, userId);
        if (session.getSessionStatus() != AssessmentSessionStatus.ACTIVE) {
            throw new InvalidStateException("Session is not recoverable");
        }
        StudentAttempt attempt = attemptRepository.findById(session.getAttemptId())
                .orElseThrow(() -> NotFoundException.of("Attempt", session.getAttemptId()));
        if (attempt.getAttemptStatus() == com.company.exam.delivery.entity.AttemptStatus.SUBMITTED
                || attempt.getAttemptStatus() == com.company.exam.delivery.entity.AttemptStatus.EVALUATED) {
            throw new InvalidStateException("Attempt is already submitted");
        }
        session.setLastActivityOn(Instant.now());
        sessionRepository.save(session);
        return describe(session);
    }

    /**
     * Ends a session without submitting the attempt ({@code §56, §65}). An operator may end any
     * session when authorized.
     */
    @Transactional
    public DeliveryDtos.SessionResponse end(UUID sessionId, UUID userId, boolean asOperator) {
        AssessmentSession session = asOperator
                ? sessionRepository.findById(sessionId)
                        .orElseThrow(() -> NotFoundException.of("Session", sessionId))
                : requireOwned(sessionId, userId);
        if (session.getSessionStatus() == AssessmentSessionStatus.ACTIVE) {
            session.setSessionStatus(AssessmentSessionStatus.LOGGED_OUT);
            session.setEndedOn(Instant.now());
            sessionRepository.save(session);
        }
        return describe(session);
    }

    /** Records meaningful runtime activity ({@code §48}). */
    @Transactional
    public void touch(UUID attemptId, Instant now) {
        sessionRepository.findFirstByAttemptIdAndSessionStatusOrderByStartedOnDesc(attemptId,
                AssessmentSessionStatus.ACTIVE).ifPresent(session -> {
                    session.setLastActivityOn(now);
                    sessionRepository.save(session);
                });
    }

    /**
     * Transitions the active session after a submission. It only acts on an ACTIVE session, so it
     * never double-ends ({@code §64, §66}).
     */
    @Transactional
    public void endActiveForAttempt(UUID attemptId, Instant now) {
        sessionRepository.findFirstByAttemptIdAndSessionStatusOrderByStartedOnDesc(attemptId,
                AssessmentSessionStatus.ACTIVE).ifPresent(session -> {
                    session.setSessionStatus(AssessmentSessionStatus.LOGGED_OUT);
                    session.setEndedOn(now);
                    sessionRepository.save(session);
                });
    }

    @Transactional
    public AssessmentSession requireOwnedSession(UUID sessionId, UUID userId) {
        return requireOwned(sessionId, userId);
    }

    private AssessmentSession requireOwned(UUID sessionId, UUID userId) {
        return sessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> NotFoundException.of("Session", sessionId));
    }

    private DeliveryDtos.SessionResponse describe(AssessmentSession session) {
        StudentAttempt attempt = attemptRepository.findById(session.getAttemptId())
                .orElseThrow(() -> NotFoundException.of("Attempt", session.getAttemptId()));
        AssessmentDefinitionCatalog.ScheduleView schedule = assessmentCatalog
                .requireSchedule(session.getAssessmentScheduleId());
        AssessmentDefinitionCatalog.PublishedAssessmentVersion version = assessmentCatalog
                .requirePublishedVersion(attempt.getAssessmentVersionId());
        List<String> sections = questionRepository.findDistinctSectionNames(attempt.getId());
        DeliveryConfiguration configuration = runtimeConfigurationService.resolve(schedule, version, sections);
        return new DeliveryDtos.SessionResponse(session.getId(), session.getAttemptId(),
                session.getAssessmentScheduleId(),
                session.getSessionStatus() == null ? null : session.getSessionStatus().name(),
                session.getStartedOn(), session.getLastActivityOn(), session.getEndedOn(), Instant.now(),
                DeliveryTimers.timer(attempt, configuration));
    }
}
