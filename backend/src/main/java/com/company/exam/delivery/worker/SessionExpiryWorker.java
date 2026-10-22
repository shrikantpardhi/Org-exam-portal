package com.company.exam.delivery.worker;

import com.company.exam.delivery.application.SessionExpiryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * The session expiry worker ({@code specs/14-P5-Delivery-V1.5.md §90}).
 *
 * <p>It identifies stale ACTIVE sessions, claims and transitions them safely, sets {@code ended_on},
 * and leaves submitted attempt state untouched. Session expiration does not delete runtime academic
 * data.
 *
 * <p>The sweep is bounded and runs on its own schedule so it cannot compete with the snapshot
 * preparation worker, the attempt expiry worker, or live examination traffic.
 */
@Component
@ConditionalOnProperty(name = "app.delivery.worker.enabled", havingValue = "true")
public class SessionExpiryWorker {

    private static final Logger log = LoggerFactory.getLogger(SessionExpiryWorker.class);

    private final SessionExpiryService sessionExpiryService;

    @Value("${app.delivery.session-expiry-worker.batch-size:50}")
    private int batchSize;

    public SessionExpiryWorker(SessionExpiryService sessionExpiryService) {
        this.sessionExpiryService = sessionExpiryService;
    }

    @Scheduled(fixedDelayString = "${app.delivery.session-expiry-worker.interval:PT60S}",
            initialDelayString = "${app.delivery.session-expiry-worker.initial-delay:PT30S}")
    public void expireStaleSessions() {
        int expired = sessionExpiryService.sweep(batchSize);
        if (expired > 0) {
            log.info("Session expiry worker expired {} stale assessment session(s)", expired);
        }
    }
}
