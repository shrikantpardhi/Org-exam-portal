package com.company.exam.communication.service;

import com.company.exam.communication.dto.CommunicationDtos;
import com.company.exam.communication.entity.Notification;
import com.company.exam.communication.entity.NotificationStatus;
import com.company.exam.communication.observability.CommunicationMetrics;
import com.company.exam.communication.repository.NotificationRepository;
import com.company.exam.common.exception.NotFoundException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Default {@link NotificationService} ({@code specs/19-P10-Communication-V1.5.md §8, §9, §12, §48}).
 *
 * <p>Notifications are created in batches. Read state is transactional and user-specific, so
 * notification lists are deliberately not cached (§63).
 */
@Service
public class NotificationServiceImpl implements NotificationService {

    private static final int MAX_TITLE = 255;

    private final NotificationRepository notificationRepository;
    private final CommunicationMetrics metrics;

    public NotificationServiceImpl(NotificationRepository notificationRepository, CommunicationMetrics metrics) {
        this.notificationRepository = notificationRepository;
        this.metrics = metrics;
    }

    @Override
    @Transactional
    public int notifyUsers(Collection<UUID> userIds, String title, String message) {
        if (userIds == null || userIds.isEmpty()) {
            return 0;
        }
        Collection<UUID> distinct = new LinkedHashSet<>(userIds);
        Instant now = Instant.now();
        List<Notification> batch = new ArrayList<>(distinct.size());
        for (UUID userId : distinct) {
            if (userId == null) {
                continue;
            }
            Notification notification = new Notification();
            notification.setUserId(userId);
            notification.setTitle(truncate(title));
            notification.setMessage(message);
            notification.setNotificationStatus(NotificationStatus.UNREAD);
            notification.setCreatedOn(now);
            batch.add(notification);
        }
        notificationRepository.saveAll(batch);
        metrics.notificationsCreated(batch.size());
        return batch.size();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<CommunicationDtos.NotificationView> listForUser(UUID userId, NotificationStatus status,
            Pageable pageable) {
        Page<Notification> page = status == null
                ? notificationRepository.findByUserIdOrderByCreatedOnDesc(userId, pageable)
                : notificationRepository.findByUserIdAndNotificationStatusOrderByCreatedOnDesc(userId, status,
                        pageable);
        return page.map(this::toView);
    }

    @Override
    @Transactional(readOnly = true)
    public long unreadCount(UUID userId) {
        return notificationRepository.countByUserIdAndNotificationStatus(userId, NotificationStatus.UNREAD);
    }

    @Override
    @Transactional
    public void markRead(UUID userId, UUID notificationId) {
        Notification notification = notificationRepository.findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> NotFoundException.of("Notification", notificationId));
        if (notification.getNotificationStatus() != NotificationStatus.READ) {
            notification.setNotificationStatus(NotificationStatus.READ);
            notificationRepository.save(notification);
        }
    }

    @Override
    @Transactional
    public int markAllRead(UUID userId) {
        return notificationRepository.markAllRead(userId, NotificationStatus.UNREAD, NotificationStatus.READ);
    }

    private String truncate(String title) {
        if (title == null) {
            return null;
        }
        return title.length() <= MAX_TITLE ? title : title.substring(0, MAX_TITLE);
    }

    private CommunicationDtos.NotificationView toView(Notification notification) {
        return new CommunicationDtos.NotificationView(notification.getId(), notification.getTitle(),
                notification.getMessage(),
                notification.getNotificationStatus() == null ? null : notification.getNotificationStatus().name(),
                notification.getCreatedOn());
    }
}
