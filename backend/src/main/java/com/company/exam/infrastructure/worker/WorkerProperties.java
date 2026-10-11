package com.company.exam.infrastructure.worker;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Background worker and scheduler configuration.
 *
 * <p>The platform uses asynchronous processing for long-running work
 * ({@code specs/03-Technical-Design-Document-V1.5.md §57}). P0 establishes the worker lifecycle
 * foundation only; it does not create a job table or any business worker
 * ({@code specs/09-P0-Foundation-V1.5.md §57, §140}).
 */
@ConfigurationProperties(prefix = "app.worker")
public class WorkerProperties {

    /** Core scheduler pool size. Not a hardened capacity figure; tuned under load testing. */
    private int schedulerPoolSize = 4;

    /** Seconds to wait for in-flight worker tasks during graceful shutdown. */
    private int shutdownAwaitSeconds = 20;

    public int getSchedulerPoolSize() {
        return schedulerPoolSize;
    }

    public void setSchedulerPoolSize(int schedulerPoolSize) {
        this.schedulerPoolSize = schedulerPoolSize;
    }

    public int getShutdownAwaitSeconds() {
        return shutdownAwaitSeconds;
    }

    public void setShutdownAwaitSeconds(int shutdownAwaitSeconds) {
        this.shutdownAwaitSeconds = shutdownAwaitSeconds;
    }
}
