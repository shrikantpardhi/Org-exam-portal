package com.company.exam.infrastructure.worker;

import com.company.exam.common.exception.SystemException;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Executes background work with consistent structured logging, timing, and metrics.
 *
 * <p>This is the worker execution boundary established by P0
 * ({@code specs/09-P0-Foundation-V1.5.md §57, §63}). It centralizes the retry-and-error logging
 * convention so that later feature workers (report generation, analytics aggregation, snapshot
 * preparation, and so on) behave consistently. It does not itself retry: retry safety is a
 * property of the task, decided by the owning feature.
 */
@Component
public class WorkerExecutionTemplate {

    private static final Logger log = LoggerFactory.getLogger(WorkerExecutionTemplate.class);

    private static final String METRIC_DURATION = "exam.worker.duration";
    private static final String METRIC_EXECUTIONS = "exam.worker.executions";

    private final MeterRegistry meterRegistry;

    public WorkerExecutionTemplate(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    /**
     * Runs a task, recording success/failure counts and duration.
     *
     * @param jobType a stable identifier used as a metric tag
     * @param task    the work to run
     * @throws SystemException if the task fails
     */
    public void execute(String jobType, WorkerTask task) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            task.run();
            meterRegistry.counter(METRIC_EXECUTIONS, "jobType", jobType, "outcome", "success").increment();
            log.info("background_operation operation={} status=SUCCESS", jobType);
        } catch (Exception failure) {
            meterRegistry.counter(METRIC_EXECUTIONS, "jobType", jobType, "outcome", "failure").increment();
            log.error("background_operation operation={} status=FAILURE", jobType, failure);
            throw new SystemException("Background operation failed: " + jobType, failure);
        } finally {
            sample.stop(meterRegistry.timer(METRIC_DURATION, "jobType", jobType));
        }
    }
}
