package com.company.exam.delivery.application;

import com.company.exam.delivery.entity.AttemptStatus;
import com.company.exam.delivery.entity.StudentAttempt;
import com.company.exam.delivery.repository.StudentAttemptRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Expiry auto-submission ({@code specs/14-P5-Delivery-V1.5.md §56, §67}).
 *
 * <p>When the deadline has passed, the server finalizes the attempt through the <em>same</em> server
 * submission path a manual submit uses: a conditional {@code IN_PROGRESS -> SUBMITTED} transition
 * that ends the session and hands off to evaluation.
 *
 * <p>Only the winning transition performs the handoff, so expiry and a concurrent manual submit
 * produce exactly one logical submission and exactly one evaluation job ({@code §56}: "Timer expiry
 * is race-safe with manual submission. Only one finalization wins logically.").
 */
@Service
public class AttemptExpiryService {

    private final StudentAttemptRepository attemptRepository;
    private final SubmissionService submissionService;
    private final DeadlineEnforcementService deadlineEnforcementService;

    public AttemptExpiryService(StudentAttemptRepository attemptRepository, SubmissionService submissionService,
            DeadlineEnforcementService deadlineEnforcementService) {
        this.attemptRepository = attemptRepository;
        this.submissionService = submissionService;
        this.deadlineEnforcementService = deadlineEnforcementService;
    }

    /**
     * Submits one attempt whose deadline has passed.
     *
     * @return {@code true} when this caller performed the finalization
     */
    @Transactional
    public boolean finalizeIfExpired(UUID attemptId) {
        StudentAttempt attempt = attemptRepository.findById(attemptId).orElse(null);
        if (attempt == null || attempt.getAttemptStatus() != AttemptStatus.IN_PROGRESS) {
            return false;
        }
        if (!deadlineEnforcementService.isExpired(attempt)) {
            return false;
        }
        // Auto-submit is a CONFIGURED behaviour (section 56): a policy that does not enable it
        // leaves the attempt to the student's manual submission.
        boolean autoSubmit = deadlineEnforcementService.configurationFor(attempt).submission().autoSubmitOnExpiry();
        if (!autoSubmit) {
            return false;
        }
        return submissionService.finalizeExpired(attemptId);
    }

    /**
     * Sweeps the in-progress attempts, oldest first, and finalizes the expired ones.
     *
     * <p>The candidate page is bounded and ordered by {@code started_on}, so the attempts most likely
     * to be expired are examined first and no unbounded scan is performed.
     */
    @Transactional
    public int sweep(int pageSize) {
        List<UUID> candidates = attemptRepository.findInProgressIdsOrderByStartedOnAsc(AttemptStatus.IN_PROGRESS,
                Pageable.ofSize(Math.max(1, pageSize)));
        int submitted = 0;
        for (UUID attemptId : candidates) {
            if (finalizeIfExpired(attemptId)) {
                submitted++;
            }
        }
        return submitted;
    }

    /** The current server time, used as the single time source for the sweep. */
    Instant now() {
        return Instant.now();
    }
}
