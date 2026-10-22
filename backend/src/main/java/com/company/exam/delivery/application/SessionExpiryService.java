package com.company.exam.delivery.application;

import com.company.exam.common.audit.CurrentAuditorProvider;
import com.company.exam.common.audit.SecurityEventLogger;
import com.company.exam.delivery.config.DeliverySessionProperties;
import com.company.exam.delivery.entity.AssessmentSession;
import com.company.exam.delivery.entity.AssessmentSessionStatus;
import com.company.exam.delivery.entity.AttemptStatus;
import com.company.exam.delivery.entity.StudentAttempt;
import com.company.exam.delivery.repository.AssessmentSessionRepository;
import com.company.exam.delivery.repository.StudentAttemptRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The assessment-session expiry sweep ({@code specs/14-P5-Delivery-V1.5.md §90, §91}).
 *
 * <p>Session expiry and attempt expiry are distinct concepts (§91). Session expiry means the runtime
 * connection is no longer active; attempt expiry means the examination deadline has passed and the
 * attempt can no longer accept normal mutations. A session may expire while its attempt remains
 * recoverable, and an attempt deadline may pass while a session remains technically connected. The
 * server decides the valid action in both cases.
 *
 * <p>The frozen {@code assessment_session} table has no expiry column, so the expiry is <em>derived</em>
 * from persisted state:
 *
 * <ul>
 *   <li><b>Idle.</b> No runtime activity since {@code last_activity_on} for longer than the
 *       configured idle timeout ({@code app.delivery.session.idle-timeout}, default two hours). Runtime
 *       activity is recorded on every autosave, so only an abandoned connection goes quiet.
 *   <li><b>Finished attempt.</b> The attempt deadline has passed <em>and</em> the attempt is no longer
 *       {@code IN_PROGRESS}. The attempt's own expiry path owns the {@code IN_PROGRESS -> SUBMITTED}
 *       transition; this sweep only closes a session left ACTIVE behind an attempt that is already over.
 * </ul>
 *
 * <p>The sweep never mutates an attempt: it transitions the session row only, so a submitted attempt
 * is never changed (§90: "avoids changing submitted attempt state incorrectly"). Session expiration
 * does not delete runtime academic data.
 *
 * <p>The transition is a conditional {@code ACTIVE -> EXPIRED} update, so the affected-row count is
 * the ownership signal: exactly one sweeper moves a session, a retry affects no rows, and the audit
 * event is emitted exactly once.
 */
@Service
public class SessionExpiryService {

    private final AssessmentSessionRepository sessionRepository;
    private final StudentAttemptRepository attemptRepository;
    private final DeadlineEnforcementService deadlineEnforcement;
    private final SecurityEventLogger audit;
    private final DeliverySessionProperties properties;
    private final com.company.exam.delivery.observability.DeliveryMetrics metrics;

    public SessionExpiryService(AssessmentSessionRepository sessionRepository,
            StudentAttemptRepository attemptRepository, DeadlineEnforcementService deadlineEnforcement,
            SecurityEventLogger audit, DeliverySessionProperties properties,
            com.company.exam.delivery.observability.DeliveryMetrics metrics) {
        this.sessionRepository = sessionRepository;
        this.attemptRepository = attemptRepository;
        this.deadlineEnforcement = deadlineEnforcement;
        this.audit = audit;
        this.properties = properties;
        this.metrics = metrics;
    }

    /**
     * Expires one session when its derived expiry has passed.
     *
     * @return {@code true} when this caller performed the transition
     */
    @Transactional
    public boolean expireIfStale(UUID sessionId, Instant now) {
        AssessmentSession session = sessionRepository.findById(sessionId).orElse(null);
        if (session == null || session.getSessionStatus() != AssessmentSessionStatus.ACTIVE) {
            return false;
        }
        StudentAttempt attempt = attemptRepository.findById(session.getAttemptId()).orElse(null);
        if (!isStale(session, attempt, now)) {
            return false;
        }
        int updated = sessionRepository.markExpired(sessionId, AssessmentSessionStatus.ACTIVE,
                AssessmentSessionStatus.EXPIRED, now);
        if (updated == 0) {
            // Another sweeper won the transition; nothing to report and nothing to duplicate.
            return false;
        }
        audit.audit(CurrentAuditorProvider.SYSTEM_ACTOR_ID, "EXPIRE", "AssessmentSession", sessionId,
                "Assessment session expired by the session expiry sweep");
        metrics.sessionExpired();
        return true;
    }

    /**
     * Sweeps the ACTIVE sessions, least recently active first, and expires the stale ones.
     *
     * <p>The candidate page is bounded and ordered by {@code last_activity_on}, so the sessions most
     * likely to be stale are examined first and no unbounded scan is performed.
     */
    @Transactional
    public int sweep(int pageSize) {
        List<UUID> candidates = sessionRepository.findActiveIdsOrderedByLastActivityOnAsc(
                AssessmentSessionStatus.ACTIVE, Pageable.ofSize(Math.max(1, pageSize)));
        Instant now = Instant.now();
        int expired = 0;
        for (UUID sessionId : candidates) {
            if (expireIfStale(sessionId, now)) {
                expired++;
            }
        }
        return expired;
    }

    /** Whether the session has passed its derived expiry. */
    private boolean isStale(AssessmentSession session, StudentAttempt attempt, Instant now) {
        Instant lastActivity = session.getLastActivityOn();
        if (lastActivity != null
                && !now.isBefore(lastActivity.plus(properties.getIdleTimeout()))) {
            return true;
        }
        if (attempt == null || attempt.getAttemptStatus() == AttemptStatus.IN_PROGRESS) {
            return false;
        }
        // The attempt is over. The session is stale only once the deadline has also passed, so a
        // still-running attempt keeps its session (attempt expiry and session expiry are distinct,
        // §91) and the attempt's own expiry path stays the single owner of the attempt transition.
        return deadlineEnforcement.deadlineFor(attempt).map(deadline -> !now.isBefore(deadline)).orElse(false);
    }
}
