package com.company.exam.common.job;

/** Frozen {@code background_job.status} values ({@code specs/04-Database-Schema-V1.5.md §35.1}). */
public enum BackgroundJobStatus {
    QUEUED,
    RUNNING,
    COMPLETED,
    COMPLETED_WITH_ERRORS,
    FAILED,
    CANCELLED
}
