package com.company.exam.delivery.application;

import com.company.exam.assessment.api.AssessmentDefinitionCatalog;
import com.company.exam.common.exception.InvalidStateException;
import com.company.exam.common.exception.NotFoundException;
import com.company.exam.delivery.entity.StudentAttempt;
import com.company.exam.delivery.repository.StudentAttemptRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Start Exam access and eligibility boundary ({@code specs/14-P5-Delivery-V1.5.md §18, §19}).
 *
 * <p>Authorization is based on the authenticated user and the resolved resource, never on a
 * client-provided role. The schedule window check uses server time ({@code §54}).
 */
@Service
public class AttemptEligibilityService {

    private final StudentAttemptRepository attemptRepository;
    private final AssessmentDefinitionCatalog assessmentCatalog;

    public AttemptEligibilityService(StudentAttemptRepository attemptRepository,
            AssessmentDefinitionCatalog assessmentCatalog) {
        this.attemptRepository = attemptRepository;
        this.assessmentCatalog = assessmentCatalog;
    }

    /** Resolves the schedule and verifies it is active and open at the current server time. */
    @Transactional(readOnly = true)
    public AssessmentDefinitionCatalog.ScheduleView requireOpenSchedule(UUID scheduleId) {
        AssessmentDefinitionCatalog.ScheduleView schedule = assessmentCatalog.requireSchedule(scheduleId);
        Instant now = Instant.now();
        if (schedule.status() != null && !"ACTIVE".equals(schedule.status())) {
            throw new InvalidStateException("Schedule is not active");
        }
        if (schedule.startTime() != null && now.isBefore(schedule.startTime())) {
            throw new InvalidStateException("Assessment window has not opened");
        }
        if (schedule.endTime() != null && now.isAfter(schedule.endTime())) {
            throw new InvalidStateException("Assessment window has closed");
        }
        return schedule;
    }

    /** Returns the attempt only when it belongs to the authenticated user. */
    @Transactional(readOnly = true)
    public StudentAttempt requireOwned(UUID attemptId, UUID userId) {
        return attemptRepository.findByIdAndUserId(attemptId, userId)
                .orElseThrow(() -> NotFoundException.of("Attempt", attemptId));
    }

    /** All attempts of this user for the schedule (typically one). */
    @Transactional(readOnly = true)
    public List<StudentAttempt> attemptsFor(UUID scheduleId, UUID userId) {
        return attemptRepository.findByAssessmentScheduleIdAndUserId(scheduleId, userId);
    }
}
