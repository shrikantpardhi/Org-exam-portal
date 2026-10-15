package com.company.exam.communication.service;

import com.company.exam.common.audit.SecurityEventLogger;
import com.company.exam.common.enums.Status;
import com.company.exam.common.exception.InvalidStateException;
import com.company.exam.common.exception.NotFoundException;
import com.company.exam.common.exception.ValidationException;
import com.company.exam.common.job.BackgroundJob;
import com.company.exam.common.job.BackgroundJobRepository;
import com.company.exam.common.job.BackgroundJobStatus;
import com.company.exam.common.job.JobTypes;
import com.company.exam.common.api.ApiError;
import com.company.exam.communication.dto.CommunicationDtos;
import com.company.exam.communication.entity.Announcement;
import com.company.exam.communication.observability.CommunicationMetrics;
import com.company.exam.communication.repository.AnnouncementRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Announcement administration and publication
 * ({@code specs/19-P10-Communication-V1.5.md §39-§47, §68}).
 *
 * <p>Publication never fans out synchronously in the request: it validates, sets the status ACTIVE,
 * and enqueues a durable fan-out job ({@code §46, §47}). The student view applies status, date range
 * and server time before returning rows ({@code §44, §45}).
 */
@Service
public class AnnouncementService {

    private final AnnouncementRepository announcementRepository;
    private final BackgroundJobRepository jobRepository;
    private final CommunicationMetrics metrics;
    private final SecurityEventLogger audit;

    public AnnouncementService(AnnouncementRepository announcementRepository, BackgroundJobRepository jobRepository,
            CommunicationMetrics metrics, SecurityEventLogger audit) {
        this.announcementRepository = announcementRepository;
        this.jobRepository = jobRepository;
        this.metrics = metrics;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public Page<Announcement> listAdmin(Status status, Pageable pageable) {
        return announcementRepository.findAll((root, query, cb) -> status == null
                ? cb.conjunction()
                : cb.equal(root.get("status"), status), pageable);
    }

    /** The student-visible view: ACTIVE, within the inclusive date window ({@code §44, §45, §68}). */
    @Transactional(readOnly = true)
    public List<CommunicationDtos.AnnouncementResponse> listVisible() {
        Instant now = Instant.now();
        return announcementRepository.findByStatus(Status.ACTIVE).stream()
                .filter(announcement -> announcement.getStartDate() == null
                        || !announcement.getStartDate().isAfter(now))
                .filter(announcement -> announcement.getEndDate() == null
                        || !announcement.getEndDate().isBefore(now))
                .sorted(java.util.Comparator.comparing(Announcement::getCreatedOn,
                        java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder())))
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public CommunicationDtos.AnnouncementResponse get(UUID id) {
        return toResponse(require(id));
    }

    /** Creates a draft announcement (INACTIVE); publication is an explicit action ({@code §41}). */
    @Transactional
    public CommunicationDtos.AnnouncementResponse create(CommunicationDtos.CreateAnnouncementRequest request,
            UUID actorId) {
        validateWindow(request.startDate(), request.endDate());
        Announcement announcement = new Announcement();
        announcement.setTitle(request.title().trim());
        announcement.setDescription(request.description());
        announcement.setStartDate(request.startDate());
        announcement.setEndDate(request.endDate());
        announcement.setStatus(Status.INACTIVE);
        Announcement saved = announcementRepository.saveAndFlush(announcement);
        audit.audit(actorId, "CREATE", "Announcement", saved.getId(), "Created draft announcement");
        return toResponse(saved);
    }

    @Transactional
    public CommunicationDtos.AnnouncementResponse update(UUID id,
            CommunicationDtos.UpdateAnnouncementRequest request, UUID actorId) {
        Announcement announcement = require(id);
        if (request.title() != null) {
            announcement.setTitle(request.title().trim());
        }
        if (request.description() != null) {
            announcement.setDescription(request.description());
        }
        Instant start = request.startDate() != null ? request.startDate() : announcement.getStartDate();
        Instant end = request.endDate() != null ? request.endDate() : announcement.getEndDate();
        validateWindow(start, end);
        announcement.setStartDate(start);
        announcement.setEndDate(end);
        Announcement saved = announcementRepository.save(announcement);
        audit.audit(actorId, "UPDATE", "Announcement", saved.getId(), "Updated announcement");
        return toResponse(saved);
    }

    @Transactional
    public void archive(UUID id, UUID actorId) {
        Announcement announcement = require(id);
        announcement.setStatus(Status.ARCHIVED);
        announcementRepository.save(announcement);
        audit.audit(actorId, "ARCHIVE", "Announcement", announcement.getId(), "Archived announcement");
    }

    /**
     * Publishes an announcement and queues the fan-out ({@code §43, §46, §47, §129}).
     *
     * <p>The action returns as soon as the announcement is ACTIVE and the job is durable; the
     * recipients are resolved and written by the fan-out worker.
     */
    @Transactional
    public CommunicationDtos.PublishAnnouncementResponse publish(UUID id, UUID actorId) {
        Announcement announcement = require(id);
        if (announcement.getTitle() == null || announcement.getTitle().isBlank()) {
            throw new ValidationException("Announcement title is required",
                    List.of(ApiError.of("title", "TITLE_REQUIRED", "A title is required to publish")));
        }
        if (announcement.getStatus() == Status.ARCHIVED) {
            throw new InvalidStateException("An archived announcement cannot be published");
        }
        validateWindow(announcement.getStartDate(), announcement.getEndDate());
        announcement.setStatus(Status.ACTIVE);
        Announcement saved = announcementRepository.saveAndFlush(announcement);

        BackgroundJob job = new BackgroundJob();
        job.setJobType(JobTypes.ANNOUNCEMENT_FANOUT);
        job.setReferenceType("ANNOUNCEMENT");
        job.setReferenceId(saved.getId());
        job.setInitiatedBy(actorId);
        job.setStatus(BackgroundJobStatus.QUEUED);
        job.setParameters("{\"announcementId\":\"" + saved.getId() + "\"}");
        job.setQueuedOn(Instant.now());
        job.setProgressCurrent(0);
        job.setProgressTotal(0);
        job.setAttemptCount(0);
        BackgroundJob savedJob = jobRepository.save(job);

        audit.audit(actorId, "PUBLISH", "Announcement", saved.getId(), "Published announcement and queued fan-out");
        metrics.announcementFanout(0);
        return new CommunicationDtos.PublishAnnouncementResponse(saved.getId(), saved.getStatus().name(),
                savedJob.getId());
    }

    private void validateWindow(Instant start, Instant end) {
        if (start != null && end != null && end.isBefore(start)) {
            throw new ValidationException("Announcement date range is invalid",
                    List.of(ApiError.of("endDate", "INVALID_DATE_RANGE", "endDate must not precede startDate")));
        }
    }

    private Announcement require(UUID id) {
        return announcementRepository.findById(id).orElseThrow(() -> NotFoundException.of("Announcement", id));
    }

    private CommunicationDtos.AnnouncementResponse toResponse(Announcement announcement) {
        return new CommunicationDtos.AnnouncementResponse(announcement.getId(), announcement.getTitle(),
                announcement.getDescription(), announcement.getStartDate(), announcement.getEndDate(),
                announcement.getStatus() == null ? null : announcement.getStatus().name());
    }
}
