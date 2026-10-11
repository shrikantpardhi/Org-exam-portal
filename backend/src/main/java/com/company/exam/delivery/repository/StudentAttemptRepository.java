package com.company.exam.delivery.repository;

import com.company.exam.delivery.entity.SnapshotStatus;
import com.company.exam.delivery.entity.StudentAttempt;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Persistence for {@link StudentAttempt} ({@code specs/14-P5-Delivery-V1.5.md §84}).
 *
 * <p>Every state transition is expressed as a conditional bulk update so concurrent workers and
 * concurrent Start requests race safely on the database, with no in-process lock
 * ({@code §22, §27}). The number of affected rows is the ownership signal.
 */
public interface StudentAttemptRepository extends JpaRepository<StudentAttempt, UUID> {

    Optional<StudentAttempt> findByIdAndUserId(UUID id, UUID userId);

    List<StudentAttempt> findByUserIdOrderByStartedOnDesc(UUID userId);

    List<StudentAttempt> findByAssessmentScheduleIdOrderByCreatedOnAsc(UUID assessmentScheduleId);

    List<StudentAttempt> findByAssessmentScheduleIdAndSnapshotStatus(UUID assessmentScheduleId,
            SnapshotStatus snapshotStatus);

    List<StudentAttempt> findByAssessmentScheduleIdAndUserId(UUID assessmentScheduleId, UUID userId);

    long countByAssessmentScheduleIdAndSnapshotStatus(UUID assessmentScheduleId, SnapshotStatus snapshotStatus);

    boolean existsByUserIdAndAssessmentScheduleIdAndAttemptStatusNot(UUID userId, UUID assessmentScheduleId,
            com.company.exam.delivery.entity.AttemptStatus attemptStatus);

    /**
     * Races to claim a PENDING attempt for preparation. Only the transaction that receives one
     * affected row owns the preparation ({@code §22}).
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update StudentAttempt a
               set a.snapshotStatus = :preparing,
                   a.updatedOn = :now
             where a.id = :attemptId
               and a.snapshotStatus = :pending
            """)
    int claimForPreparation(@Param("attemptId") UUID attemptId,
            @Param("pending") SnapshotStatus pending,
            @Param("preparing") SnapshotStatus preparing,
            @Param("now") Instant now);

    /** Marks a PREPARING attempt READY once the snapshot is fully materialized ({@code §21}). */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update StudentAttempt a
               set a.snapshotStatus = :ready,
                   a.snapshotPreparedOn = :now,
                   a.updatedOn = :now
             where a.id = :attemptId
               and a.snapshotStatus = :preparing
            """)
    int markSnapshotReady(@Param("attemptId") UUID attemptId,
            @Param("preparing") SnapshotStatus preparing,
            @Param("ready") SnapshotStatus ready,
            @Param("now") Instant now);

    /** Marks a PREPARING attempt FAILED; retry is explicit ({@code §23}). */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update StudentAttempt a
               set a.snapshotStatus = :failed,
                   a.updatedOn = :now
             where a.id = :attemptId
               and a.snapshotStatus = :preparing
            """)
    int markSnapshotFailed(@Param("attemptId") UUID attemptId,
            @Param("preparing") SnapshotStatus preparing,
            @Param("failed") SnapshotStatus failed,
            @Param("now") Instant now);

    /**
     * The race-safe Start Exam transition: {@code NOT_STARTED -> IN_PROGRESS} only when the snapshot
     * is READY. A second concurrent Start sees zero affected rows ({@code §27}).
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update StudentAttempt a
               set a.attemptStatus = :inProgress,
                   a.startedOn = :now,
                   a.updatedBy = :actorId,
                   a.updatedOn = :now
             where a.id = :attemptId
               and a.attemptStatus = :notStarted
               and a.snapshotStatus = :ready
            """)
    int markStarted(@Param("attemptId") UUID attemptId,
            @Param("notStarted") com.company.exam.delivery.entity.AttemptStatus notStarted,
            @Param("inProgress") com.company.exam.delivery.entity.AttemptStatus inProgress,
            @Param("ready") SnapshotStatus ready,
            @Param("actorId") UUID actorId,
            @Param("now") Instant now);

    /** The idempotent submission transition: {@code IN_PROGRESS -> SUBMITTED} exactly once. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update StudentAttempt a
               set a.attemptStatus = :submitted,
                   a.submittedOn = :now,
                   a.updatedBy = :actorId,
                   a.updatedOn = :now
             where a.id = :attemptId
               and a.attemptStatus = :inProgress
            """)
    int markSubmitted(@Param("attemptId") UUID attemptId,
            @Param("inProgress") com.company.exam.delivery.entity.AttemptStatus inProgress,
            @Param("submitted") com.company.exam.delivery.entity.AttemptStatus submitted,
            @Param("actorId") UUID actorId,
            @Param("now") Instant now);

    /** Reclaims attempts whose PREPARING worker died; uses the approved existing timestamps. */
    @Query("""
            select a.id from StudentAttempt a
             where a.snapshotStatus = :preparing
               and a.updatedOn < :staleBefore
            """)
    List<UUID> findStalePreparing(@Param("preparing") SnapshotStatus preparing,
            @Param("staleBefore") Instant staleBefore);

    /** Resets a FAILED attempt to PENDING for an explicit retry ({@code §23}). */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update StudentAttempt a
               set a.snapshotStatus = :pending,
                   a.updatedOn = :now
             where a.id = :attemptId
               and a.snapshotStatus = :failed
            """)
    int resetForRetry(@Param("attemptId") UUID attemptId,
            @Param("failed") SnapshotStatus failed,
            @Param("pending") SnapshotStatus pending,
            @Param("now") Instant now);

    /** A bounded page of PENDING attempt ids for the preparation worker ({@code §103}). */
    @Query("select a.id from StudentAttempt a where a.snapshotStatus = :pending order by a.createdOn asc")
    List<UUID> findPendingIds(@Param("pending") SnapshotStatus pending,
            org.springframework.data.domain.Pageable pageable);
}
