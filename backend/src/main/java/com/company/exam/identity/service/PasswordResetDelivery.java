package com.company.exam.identity.service;

import com.company.exam.identity.entity.User;

/**
 * Delivers a password-reset token to the account owner ({@code specs/10-P1-Identity-V1.5.md §24}).
 *
 * <p>The full email/notification platform is not built in P1 ({@code §184}). This boundary lets a
 * real channel be plugged in later without changing the reset contract. Implementations must never
 * log the raw token ({@code §27, §167}).
 */
public interface PasswordResetDelivery {

    void deliver(User user, String resetToken);
}
