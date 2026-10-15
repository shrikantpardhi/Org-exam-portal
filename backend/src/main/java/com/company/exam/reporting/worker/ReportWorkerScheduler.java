package com.company.exam.reporting.worker;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Drives the report worker ({@code specs/22-P13-Reporting-V1.5.md §93, §94}).
 *
 * <p>Report work runs on its own schedule, separate from the delivery, evaluation, analytics and
 * communication schedules, so a long report cannot starve an exam-critical worker. The interval is
 * deliberately slower than the exam-critical workers.
 *
 * <p>Disabled in tests via {@code app.reporting.worker.enabled=false}; the worker's
 * {@code processQueued()} is then invoked explicitly and deterministically.
 */
@Component
@ConditionalOnProperty(name = "app.reporting.worker.enabled", havingValue = "true", matchIfMissing = true)
public class ReportWorkerScheduler {

    private final ReportWorker reportWorker;

    public ReportWorkerScheduler(ReportWorker reportWorker) {
        this.reportWorker = reportWorker;
    }

    @Scheduled(fixedDelayString = "${app.reporting.worker.interval:PT60S}",
            initialDelayString = "${app.reporting.worker.initial-delay:PT45S}")
    public void run() {
        reportWorker.processQueued();
    }
}
