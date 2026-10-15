package com.company.exam.communication.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

/**
 * Communication request and response payloads
 * ({@code specs/19-P10-Communication-V1.5.md §12, §13, §42, §46}).
 *
 * <p>Entities are never exposed; no provider payload, secret, or cross-user identifier appears.
 */
public final class CommunicationDtos {

    private CommunicationDtos() {
    }

    /** A student-safe notification projection ({@code §13}). */
    public record NotificationView(UUID id, String title, String message, String notificationStatus,
            Instant createdOn) {
    }

    public record UnreadCountResponse(long unread) {
    }

    public record CreateEmailTemplateRequest(@NotBlank @Size(max = 50) String code,
            @NotBlank @Size(max = 150) String name, @Size(max = 255) String subject, String body) {
    }

    public record UpdateEmailTemplateRequest(@Size(max = 150) String name, @Size(max = 255) String subject,
            String body) {
    }

    public record EmailTemplateResponse(UUID id, String code, String name, String subject, String body,
            String status) {
    }

    public record CreateAnnouncementRequest(@NotBlank @Size(max = 255) String title, String description,
            Instant startDate, Instant endDate) {
    }

    public record UpdateAnnouncementRequest(@Size(max = 255) String title, String description, Instant startDate,
            Instant endDate) {
    }

    public record AnnouncementResponse(UUID id, String title, String description, Instant startDate, Instant endDate,
            String status) {
    }

    /** The publish response: the announcement is published and fan-out is queued, not synchronous. */
    public record PublishAnnouncementResponse(UUID announcementId, String status, UUID fanoutJobId) {
    }
}
