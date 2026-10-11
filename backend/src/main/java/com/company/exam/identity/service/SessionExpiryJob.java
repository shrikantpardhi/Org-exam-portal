package com.company.exam.identity.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Sweeps sessions whose absolute lifetime has elapsed, marking them EXPIRED
 * ({@code specs/10-P1-Identity-V1.5.md §41, §43}).
 *
 * <p>Runs on the platform scheduler. Because the frozen schema has no expiry column, membership of
 * the EXPIRED set is derived from {@code login_time} plus the configured absolute lifetime.
 */
@Component
public class SessionExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(SessionExpiryJob.class);

    private final SessionService sessionService;

    public SessionExpiryJob(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @Scheduled(fixedDelayString = "${app.security.session.expiry-sweep-interval:PT5M}",
            initialDelayString = "${app.security.session.expiry-sweep-initial-delay:PT1M}")
    public void sweepExpiredSessions() {
        int expired = sessionService.expireStaleSessions();
        if (expired > 0) {
            log.info("Expired {} login session(s) past their absolute lifetime", expired);
        }
    }
}
