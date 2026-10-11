package com.company.exam.identity.service;

import com.company.exam.identity.entity.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Default reset delivery used until the Communication module provides a real channel.
 *
 * <p>It records that a reset was requested without ever emitting the token itself
 * ({@code specs/10-P1-Identity-V1.5.md §27, §167, §184}). A later phase replaces this with an
 * email/notification implementation.
 */
@Component
public class LoggingPasswordResetDelivery implements PasswordResetDelivery {

    private static final Logger log = LoggerFactory.getLogger(LoggingPasswordResetDelivery.class);

    @Override
    public void deliver(User user, String resetToken) {
        log.info("Password reset requested for userId={}; no delivery channel is configured in P1",
                user.getId());
    }
}
