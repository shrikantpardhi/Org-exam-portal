package com.company.exam.communication.entity;

/**
 * The frozen {@code notification.notification_status} vocabulary
 * ({@code specs/19-P10-Communication-V1.5.md §8, §9}).
 *
 * <p>Only UNREAD and READ exist; reading is a one-way state transition and there is no archived
 * state.
 */
public enum NotificationStatus {
    UNREAD,
    READ
}
