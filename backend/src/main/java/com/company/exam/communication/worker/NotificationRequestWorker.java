package com.company.exam.communication.worker;

import com.company.exam.common.job.BackgroundJob;
import com.company.exam.common.job.BackgroundJobRepository;
import com.company.exam.common.job.BackgroundJobStatus;
import com.company.exam.common.job.JobTypes;
import com.company.exam.common.job.JobStateService;
import com.company.exam.communication.config.CommunicationProperties;
import com.company.exam.communication.service.EmailDeliveryService;
import com.company.exam.communication.service.NotificationService;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

/**
 * Consumes durable notification requests and creates in-app notifications
 * ({@code specs/19-P10-Communication-V1.5.md §16, §32, §48}).
 *
 * <p>The listener only enqueues the job; this worker resolves recipients and writes notifications in
 * bounded batches, then enqueues an email delivery job where a template applies.
 *
 * <p>The bean is always present; only the scheduler that drives it is gated by
 * {@code app.communication.worker.enabled}, so tests can invoke {@link #processQueued()} directly.
 */
@Component
public class NotificationRequestWorker {

    private static final Logger log = LoggerFactory.getLogger(NotificationRequestWorker.class);

    private final BackgroundJobRepository jobRepository;
    private final NotificationService notificationService;
    private final EmailDeliveryService emailDeliveryService;
    private final JobStateService jobStateService;
    private final int batchSize;

    public NotificationRequestWorker(BackgroundJobRepository jobRepository, NotificationService notificationService,
            EmailDeliveryService emailDeliveryService, JobStateService jobStateService,
            CommunicationProperties properties) {
        this.jobRepository = jobRepository;
        this.notificationService = notificationService;
        this.emailDeliveryService = emailDeliveryService;
        this.jobStateService = jobStateService;
        this.batchSize = properties.getWorkerBatchSize();
    }

    public int processQueued() {
        List<BackgroundJob> queued = jobRepository.findByJobTypeAndStatusOrderByQueuedOnAsc(
                JobTypes.NOTIFICATION_REQUEST, BackgroundJobStatus.QUEUED, PageRequest.of(0, batchSize));
        int processed = 0;
        for (BackgroundJob job : queued) {
            if (jobStateService.claim(job.getId())) {
                process(job);
                processed++;
            }
        }
        return processed;
    }

    /** Processes one job: creates the in-app notifications and queues the email deliveries. */
    public int process(BackgroundJob job) {
        EmailDeliveryService.NotificationJobParameters parameters =
                emailDeliveryService.readNotificationJob(job.getParameters());
        List<UUID> userIds = parameters.userIds() == null ? List.of() : parameters.userIds();
        int created = notificationService.notifyUsers(userIds, parameters.title(), parameters.message());
        if (parameters.templateCode() != null && !parameters.templateCode().isBlank()) {
            for (UUID userId : userIds) {
                emailDeliveryService.enqueueEmail(userId, parameters.templateCode(), parameters.variables());
            }
        }
        jobStateService.complete(job.getId(), "notifications=" + created);
        log.debug("Notification request {} created {} notifications", job.getId(), created);
        return created;
    }
}
