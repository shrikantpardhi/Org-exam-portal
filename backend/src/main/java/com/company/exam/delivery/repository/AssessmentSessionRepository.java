package com.company.exam.delivery.repository;

import com.company.exam.delivery.entity.AssessmentSession;
import com.company.exam.delivery.entity.AssessmentSessionStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Persistence for {@link AssessmentSession} ({@code specs/14-P5-Delivery-V1.5.md §84}). */
public interface AssessmentSessionRepository extends JpaRepository<AssessmentSession, UUID> {

    Optional<AssessmentSession> findByIdAndUserId(UUID id, UUID userId);

    List<AssessmentSession> findByAttemptId(UUID attemptId);

    Optional<AssessmentSession> findFirstByAttemptIdAndSessionStatusOrderByStartedOnDesc(UUID attemptId,
            AssessmentSessionStatus status);

    List<AssessmentSession> findByUserIdAndSessionStatusOrderByLastActivityOnDesc(UUID userId,
            AssessmentSessionStatus status);

    long countByAttemptId(UUID attemptId);

    /**
     * A bounded page of ACTIVE session ids, least recently active first
     * ({@code specs/14-P5-Delivery-V1.5.md §90}).
     *
     * <p>The expiry sweep works on this page rather than in SQL, because the deadline depends on the
     * effective duration resolved from the published version and the delivery policy, and the frozen
     * schema stores no deadline column ({@code §57}). Ordering by {@code last_activity_on} puts the
     * sessions most likely to be stale first.
     */
    @Query("""
            select s.id from AssessmentSession s
             where s.sessionStatus = :status
             order by s.lastActivityOn asc
            """)
    List<UUID> findActiveIdsOrderedByLastActivityOnAsc(@Param("status") AssessmentSessionStatus status,
            Pageable pageable);

    /**
     * The idempotent session expiry transition: {@code ACTIVE -> EXPIRED} exactly once.
     *
     * <p>Conditional on the current status, so a repeated sweep affects no rows instead of moving an
     * already-expired session and rewriting {@code ended_on} ({@code §90}: the worker "claims and
     * transitions safely"). The number of affected rows is the ownership signal.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update AssessmentSession s
               set s.sessionStatus = :expired,
                   s.endedOn = :endedOn
             where s.id = :sessionId
               and s.sessionStatus = :active
            """)
    int markExpired(@Param("sessionId") UUID sessionId,
            @Param("active") AssessmentSessionStatus active,
            @Param("expired") AssessmentSessionStatus expired,
            @Param("endedOn") Instant endedOn);
}
