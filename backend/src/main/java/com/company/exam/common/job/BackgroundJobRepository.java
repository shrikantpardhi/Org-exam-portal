package com.company.exam.common.job;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence for {@link BackgroundJob} ({@code specs/04-Database-Schema-V1.5.md §35}). */
public interface BackgroundJobRepository extends JpaRepository<BackgroundJob, UUID> {

    /** Used by the evaluation handoff to enforce idempotent triggering. */
    List<BackgroundJob> findByJobTypeAndReferenceTypeAndReferenceId(String jobType, String referenceType,
            UUID referenceId);

    boolean existsByJobTypeAndReferenceTypeAndReferenceId(String jobType, String referenceType, UUID referenceId);
}
