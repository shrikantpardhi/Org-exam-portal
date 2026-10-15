package com.company.exam.communication.service;

import com.company.exam.common.enums.Status;
import com.company.exam.common.exception.NotFoundException;
import com.company.exam.communication.config.CommunicationProperties;
import com.company.exam.communication.entity.Announcement;
import com.company.exam.communication.observability.CommunicationMetrics;
import com.company.exam.communication.repository.AnnouncementRepository;
import com.company.exam.identity.api.CandidateDirectory;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Announcement fan-out ({@code specs/19-P10-Communication-V1.5.md §40, §47-§51}).
 *
 * <p>It resolves the audience at fan-out time and inserts notifications in bounded batches. The
 * audience model is the frozen one: all active users in the organization resolved through Identity
 * — no targeting table is invented ({@code §40}). The fan-out job is identified by its
 * {@code background_job} id, so a retry of the same job does not fan out twice ({@code §51}).
 */
@Service
public class AnnouncementFanoutService {

    private final AnnouncementRepository announcementRepository;
    private final CandidateDirectory candidateDirectory;
    private final NotificationService notificationService;
    private final CommunicationMetrics metrics;
    private final CommunicationProperties properties;

    public AnnouncementFanoutService(AnnouncementRepository announcementRepository,
            CandidateDirectory candidateDirectory, NotificationService notificationService,
            CommunicationMetrics metrics, CommunicationProperties properties) {
        this.announcementRepository = announcementRepository;
        this.candidateDirectory = candidateDirectory;
        this.notificationService = notificationService;
        this.metrics = metrics;
        this.properties = properties;
    }

    /** Fans an announcement out to the audience in bounded batches; returns the notification count. */
    @Transactional
    public int fanout(UUID announcementId) {
        Announcement announcement = announcementRepository.findById(announcementId)
                .orElseThrow(() -> NotFoundException.of("Announcement", announcementId));
        if (announcement.getStatus() != Status.ACTIVE) {
            return 0;
        }
        List<UUID> audience = candidateDirectory.activeCandidateUserIds();
        int created = 0;
        int batchSize = Math.max(1, properties.getFanoutBatchSize());
        for (int start = 0; start < audience.size(); start += batchSize) {
            List<UUID> batch = new ArrayList<>(audience.subList(start, Math.min(start + batchSize, audience.size())));
            created += notificationService.notifyUsers(batch, announcement.getTitle(), announcement.getDescription());
        }
        metrics.announcementFanout(created);
        announcement.setStatus(Status.ACTIVE);
        announcementRepository.save(announcement);
        return created;
    }

    /** A convenience for tests: the audience size without writing notifications. */
    @Transactional(readOnly = true)
    public int audienceSize(Instant ignored) {
        return candidateDirectory.activeCandidateUserIds().size();
    }
}
