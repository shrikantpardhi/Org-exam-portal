package com.company.exam.communication.repository;

import com.company.exam.communication.entity.Notification;
import com.company.exam.communication.entity.NotificationStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Persistence for {@link Notification} ({@code specs/19-P10-Communication-V1.5.md §7.1, §12, §79}). */
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    Page<Notification> findByUserIdOrderByCreatedOnDesc(UUID userId, Pageable pageable);

    Page<Notification> findByUserIdAndNotificationStatusOrderByCreatedOnDesc(UUID userId,
            NotificationStatus notificationStatus, Pageable pageable);

    Optional<Notification> findByIdAndUserId(UUID id, UUID userId);

    long countByUserIdAndNotificationStatus(UUID userId, NotificationStatus notificationStatus);

    /** Marks the caller's unread notifications read; scoped by user id, never organization-wide (§12). */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Notification n
               set n.notificationStatus = :read
             where n.userId = :userId
               and n.notificationStatus = :unread
            """)
    int markAllRead(@Param("userId") UUID userId, @Param("unread") NotificationStatus unread,
            @Param("read") NotificationStatus read);
}
