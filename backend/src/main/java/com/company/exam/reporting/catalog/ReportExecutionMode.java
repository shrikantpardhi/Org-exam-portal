package com.company.exam.reporting.catalog;

/**
 * How a report executes ({@code specs/22-P13-Reporting-V1.5.md §11}).
 *
 * <p>{@code SYNC} runs inside the request for small, bounded result sets. {@code ASYNC_PREFERRED}
 * queues a {@code background_job} so a large export never runs on the request thread (§7.5, §93).
 */
public enum ReportExecutionMode {
    SYNC,
    ASYNC_PREFERRED
}
