package com.company.exam.delivery.application;

import com.company.exam.assessment.api.AssessmentDefinitionCatalog;
import com.company.exam.delivery.engine.DeliveryConfiguration;
import com.company.exam.delivery.entity.StudentAttempt;
import com.company.exam.delivery.exception.AttemptExpiredException;
import com.company.exam.delivery.repository.AttemptQuestionRepository;
import com.company.exam.delivery.repository.StudentAttemptRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The single authority for attempt deadlines ({@code specs/14-P5-Delivery-V1.5.md §54, §56, §57};
 * {@code specs/08-Exam-Engine-and-CDUI-Contract-V1.5.md §14}).
 *
 * <p>The deadline is derived from the persisted {@code started_on} plus the effective duration
 * resolved from the published assessment version and the delivery policy — the same computation the
 * client is given at Start Exam, so the browser countdown and the server can never disagree. A browser
 * cannot extend an attempt by changing its local clock, because the client never supplies the
 * deadline.
 *
 * <p>Enforcement is unconditional for an <em>enforced</em> timer. Practice attempts and attempts
 * without a duration have no deadline, so nothing is rejected for them.
 */
@Service
public class DeadlineEnforcementService {

    private final StudentAttemptRepository attemptRepository;
    private final AssessmentDefinitionCatalog assessmentCatalog;
    private final AttemptQuestionRepository questionRepository;
    private final RuntimeConfigurationService runtimeConfigurationService;

    public DeadlineEnforcementService(StudentAttemptRepository attemptRepository,
            AssessmentDefinitionCatalog assessmentCatalog, AttemptQuestionRepository questionRepository,
            RuntimeConfigurationService runtimeConfigurationService) {
        this.attemptRepository = attemptRepository;
        this.assessmentCatalog = assessmentCatalog;
        this.questionRepository = questionRepository;
        this.runtimeConfigurationService = runtimeConfigurationService;
    }

    /** The authoritative deadline, or empty when the attempt has no enforced timer. */
    @Transactional(readOnly = true)
    public Optional<Instant> deadlineFor(StudentAttempt attempt) {
        if (attempt == null || attempt.getStartedOn() == null) {
            return Optional.empty();
        }
        DeliveryConfiguration.TimerPolicy timer = configurationFor(attempt).timer();
        if (!timer.enforced() || timer.durationSeconds() == null) {
            return Optional.empty();
        }
        return Optional.of(attempt.getStartedOn().plusSeconds(timer.durationSeconds()));
    }

    @Transactional(readOnly = true)
    public Optional<Instant> deadlineFor(UUID attemptId) {
        return attemptRepository.findById(attemptId).flatMap(this::deadlineFor);
    }

    /**
     * Rejects the request when the attempt's deadline has passed.
     *
     * @throws AttemptExpiredException with {@code ATTEMPT_EXPIRED} (HTTP 409)
     */
    public void assertNotExpired(StudentAttempt attempt) {
        deadlineFor(attempt).ifPresent(deadline -> {
            if (!Instant.now().isBefore(deadline)) {
                throw new AttemptExpiredException(deadline);
            }
        });
    }

    public boolean isExpired(StudentAttempt attempt) {
        return deadlineFor(attempt).map(deadline -> !Instant.now().isBefore(deadline)).orElse(false);
    }

    /**
     * Rejects an <em>active</em> attempt whose deadline has passed.
     *
     * <p>A finalized attempt is never rejected here: reading a submitted attempt is a legitimate
     * history read, and the existing state checks already refuse runtime actions on it.
     */
    public void assertActiveAttemptNotExpired(StudentAttempt attempt) {
        if (attempt == null || attempt.getAttemptStatus() != com.company.exam.delivery.entity.AttemptStatus.IN_PROGRESS) {
            return;
        }
        assertNotExpired(attempt);
    }

    /** The normalized runtime configuration of an attempt's published version. */
    @Transactional(readOnly = true)
    public DeliveryConfiguration configurationFor(StudentAttempt attempt) {
        AssessmentDefinitionCatalog.ScheduleView schedule = assessmentCatalog
                .requireSchedule(attempt.getAssessmentScheduleId());
        AssessmentDefinitionCatalog.PublishedAssessmentVersion version = assessmentCatalog
                .requirePublishedVersion(attempt.getAssessmentVersionId());
        List<String> sections = questionRepository.findDistinctSectionNames(attempt.getId());
        return runtimeConfigurationService.resolve(schedule, version, sections);
    }
}
