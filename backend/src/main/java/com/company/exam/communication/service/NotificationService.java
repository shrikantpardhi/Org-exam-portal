package com.company.exam.communication.service;

import com.company.exam.communication.dto.CommunicationDtos;
import com.company.exam.communication.entity.NotificationStatus;
import java.util.Collection;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * The in-app notification contract ({@code specs/19-P10-Communication-V1.5.md §14}).
 *
 * <p>Notification creation is an internal application contract, not a public student CRUD API.
 * There is no frozen {@code POST /api/v1/notifications} endpoint ({@code §14}).
 */
public interface NotificationService {

    /** Creates one notification per distinct recipient and returns the number created. */
    int notifyUsers(Collection<UUID> userIds, String title, String message);

    Page<CommunicationDtos.NotificationView> listForUser(UUID userId, NotificationStatus status, Pageable pageable);

    long unreadCount(UUID userId);

    /** Idempotent from the user's perspective: UNREAD -> READ, READ -> READ ({@code §9}). */
    void markRead(UUID userId, UUID notificationId);

    int markAllRead(UUID userId);
}
