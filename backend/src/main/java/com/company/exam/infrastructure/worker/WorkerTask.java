package com.company.exam.infrastructure.worker;

/**
 * A unit of background work.
 *
 * <p>Every background operation must have a defined safety property: idempotent, deduplicated,
 * or safe to retry ({@code specs/09-P0-Foundation-V1.5.md §59}). A task that violates this must
 * not be wired into a retrying scheduler.
 */
@FunctionalInterface
public interface WorkerTask {

    /**
     * Executes the work.
     *
     * @throws Exception when the work fails; the template logs and translates it
     */
    void run() throws Exception;
}
