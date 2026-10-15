package com.company.exam.communication.worker;

import com.company.exam.common.job.BackgroundJob;
import com.company.exam.common.job.BackgroundJobRepository;
import com.company.exam.common.job.BackgroundJobStatus;
import com.company.exam.common.job.JobTypes;
import com.company.exam.common.job.JobStateService;
import com.company.exam.communication.config.CommunicationProperties;
import com.company.exam.communication.service.AnnouncementFanoutService;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

/**
 * Asynchronous announcement fan-out ({@code specs/19-P10-Communication-V1.5.md §46-§51}).
 *
 * <p>Publishing only enqueues this job, so a large audience never blocks the request. The job is
 * claimed race-safely, and its completion is recorded so a retry of the same job does not fan out
 * twice ({@code §51}).
 *
 * <p>The bean is always present; only the scheduler that drives it is gated by
 * {@code app.communication.worker.enabled}, so tests can invoke {@link #processQueued()} directly.
 */
@Component
public class AnnouncementFanoutWorker {

    private static final Logger log = LoggerFactory.getLogger(AnnouncementFanoutWorker.class);

    private final BackgroundJobRepository jobRepository;
    private final AnnouncementFanoutService fanoutService;
    private final JobStateService jobStateService;
    private final int batchSize;

    public AnnouncementFanoutWorker(BackgroundJobRepository jobRepository, AnnouncementFanoutService fanoutService,
            JobStateService jobStateService, CommunicationProperties properties) {
        this.jobRepository = jobRepository;
        this.fanoutService = fanoutService;
        this.jobStateService = jobStateService;
        this.batchSize = properties.getWorkerBatchSize();
    }

    public int processQueued() {
        List<BackgroundJob> queued = jobRepository.findByJobTypeAndStatusOrderByQueuedOnAsc(
                JobTypes.ANNOUNCEMENT_FANOUT, BackgroundJobStatus.QUEUED, PageRequest.of(0, batchSize));
        int processed = 0;
        for (BackgroundJob job : queued) {
            if (jobStateService.claim(job.getId())) {
                int created = fanoutService.fanout(job.getReferenceId());
                jobStateService.complete(job.getId(), "recipients=" + created);
                log.info("Announcement {} fanned out to {} recipients", job.getReferenceId(), created);
                processed++;
            }
        }
        return processed;
    }
}
