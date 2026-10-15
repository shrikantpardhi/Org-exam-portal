package com.company.exam.common.job;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Persistence for {@link BackgroundJob} ({@code specs/04-Database-Schema-V1.5.md §35}). */
public interface BackgroundJobRepository extends JpaRepository<BackgroundJob, UUID> {

    /** Used by the evaluation handoff to enforce idempotent triggering. */
    List<BackgroundJob> findByJobTypeAndReferenceTypeAndReferenceId(String jobType, String referenceType,
            UUID referenceId);

    boolean existsByJobTypeAndReferenceTypeAndReferenceId(String jobType, String referenceType, UUID referenceId);

    /** A bounded page of queued jobs of a type, oldest first, for a worker ({@code §77, §78}). */
    List<BackgroundJob> findByJobTypeAndStatusOrderByQueuedOnAsc(String jobType, BackgroundJobStatus status,
            Pageable pageable);

    /** Operational counts for the admin health view ({@code specs/20-P11 §59}). */
    long countByStatus(BackgroundJobStatus status);

    long countByJobTypeAndStatus(String jobType, BackgroundJobStatus status);

    /** Report history, newest first ({@code specs/22-P13-Reporting-V1.5.md §29, §30}). */
    List<BackgroundJob> findByJobTypeOrderByQueuedOnDesc(String jobType, Pageable pageable);

    /**
     * Races to claim a queued job. Only the worker that receives one affected row owns the work; a
     * second worker sees zero and skips it.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update BackgroundJob j
               set j.status = :running,
                   j.startedOn = :now,
                   j.attemptCount = coalesce(j.attemptCount, 0) + 1,
                   j.lastAttemptOn = :now
             where j.id = :id
               and j.status = :queued
            """)
    int claim(@Param("id") UUID id, @Param("queued") BackgroundJobStatus queued,
            @Param("running") BackgroundJobStatus running, @Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update BackgroundJob j
               set j.status = :completed,
                   j.completedOn = :now,
                   j.resultReference = :resultReference
             where j.id = :id
            """)
    int markCompleted(@Param("id") UUID id, @Param("completed") BackgroundJobStatus completed,
            @Param("now") Instant now, @Param("resultReference") String resultReference);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update BackgroundJob j
               set j.status = :failed,
                   j.completedOn = :now,
                   j.errorCode = :errorCode,
                   j.errorMessage = :errorMessage
             where j.id = :id
            """)
    int markFailed(@Param("id") UUID id, @Param("failed") BackgroundJobStatus failed, @Param("now") Instant now,
            @Param("errorCode") String errorCode, @Param("errorMessage") String errorMessage);

    /**
     * Returns a claimed job to the queue for a bounded retry without clearing its attempt count or
     * last-attempt time, so the worker can apply the backoff policy ({@code 19-P10 §33-§36}).
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update BackgroundJob j
               set j.status = :queued
             where j.id = :id
               and j.status = :running
            """)
    int reschedule(@Param("id") UUID id, @Param("running") BackgroundJobStatus running,
            @Param("queued") BackgroundJobStatus queued);
}
