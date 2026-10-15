package com.company.exam.analytics.service;

import com.company.exam.common.audit.CurrentAuditorProvider;
import com.company.exam.common.job.BackgroundJob;
import com.company.exam.common.job.BackgroundJobRepository;
import com.company.exam.common.job.BackgroundJobStatus;
import com.company.exam.common.job.JobTypes;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Durable analytics refresh/rebuild handoff ({@code specs/17-P8-Analytics-V1.5.md §64, §94, §106}).
 *
 * <p>It reuses the existing {@code background_job} infrastructure; no analytics event table is
 * introduced ({@code §916}).
 */
@Service
public class AnalyticsJobService {

    private final BackgroundJobRepository jobRepository;

    public AnalyticsJobService(BackgroundJobRepository jobRepository) {
        this.jobRepository = jobRepository;
    }

    /** Signals an incremental refresh for one attempt ({@code §64, §66}). */
    @Transactional
    public BackgroundJob enqueueRefresh(UUID attemptId) {
        BackgroundJob job = new BackgroundJob();
        job.setJobType(JobTypes.ANALYTICS_REFRESH);
        job.setReferenceType("STUDENT_ATTEMPT");
        job.setReferenceId(attemptId);
        job.setInitiatedBy(CurrentAuditorProvider.SYSTEM_ACTOR_ID);
        job.setStatus(BackgroundJobStatus.QUEUED);
        job.setParameters("{\"attemptId\":\"" + attemptId + "\"}");
        job.setQueuedOn(Instant.now());
        job.setProgressCurrent(0);
        job.setProgressTotal(1);
        job.setAttemptCount(0);
        return jobRepository.save(job);
    }

    /** Signals a full rebuild ({@code §65}). */
    @Transactional
    public BackgroundJob enqueueRebuild() {
        BackgroundJob job = new BackgroundJob();
        job.setJobType(JobTypes.ANALYTICS_REBUILD);
        job.setInitiatedBy(CurrentAuditorProvider.SYSTEM_ACTOR_ID);
        job.setStatus(BackgroundJobStatus.QUEUED);
        job.setParameters("{}");
        job.setQueuedOn(Instant.now());
        job.setProgressCurrent(0);
        job.setProgressTotal(1);
        job.setAttemptCount(0);
        return jobRepository.save(job);
    }
}
