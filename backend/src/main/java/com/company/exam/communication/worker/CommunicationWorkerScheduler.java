package com.company.exam.communication.worker;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Drives the Communication workers ({@code specs/19-P10-Communication-V1.5.md §32, §81}).
 *
 * <p>Disabled in tests via {@code app.communication.worker.enabled=false}; the worker methods are
 * then invoked explicitly and deterministically.
 */
@Component
@ConditionalOnProperty(name = "app.communication.worker.enabled", havingValue = "true")
public class CommunicationWorkerScheduler {

    private final NotificationRequestWorker notificationRequestWorker;
    private final AnnouncementFanoutWorker announcementFanoutWorker;
    private final EmailDeliveryWorker emailDeliveryWorker;

    public CommunicationWorkerScheduler(NotificationRequestWorker notificationRequestWorker,
            AnnouncementFanoutWorker announcementFanoutWorker, EmailDeliveryWorker emailDeliveryWorker) {
        this.notificationRequestWorker = notificationRequestWorker;
        this.announcementFanoutWorker = announcementFanoutWorker;
        this.emailDeliveryWorker = emailDeliveryWorker;
    }

    @Scheduled(fixedDelayString = "${app.communication.worker.interval:PT15S}",
            initialDelayString = "${app.communication.worker.initial-delay:PT20S}")
    public void run() {
        notificationRequestWorker.processQueued();
        announcementFanoutWorker.processQueued();
        emailDeliveryWorker.processQueued();
    }
}
