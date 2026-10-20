package com.company.exam.delivery.worker;

import com.company.exam.delivery.application.AttemptExpiryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * The expiry auto-submit worker ({@code specs/14-P5-Delivery-V1.5.md §56, §101-§103}).
 *
 * <p>It finalizes attempts whose deadline has passed while they are still IN_PROGRESS, using the same
 * server submission path as a manual submit, so expiry and a concurrent manual submit produce exactly
 * one logical submission ({@code §56}).
 *
 * <p>The sweep is bounded and runs on its own schedule so it cannot compete with the snapshot
 * preparation worker or with live examination traffic.
 */
@Component
@ConditionalOnProperty(name = "app.delivery.worker.enabled", havingValue = "true")
public class AttemptExpiryWorker {

    private static final Logger log = LoggerFactory.getLogger(AttemptExpiryWorker.class);

    private final AttemptExpiryService attemptExpiryService;

    @Value("${app.delivery.expiry-worker.batch-size:50}")
    private int batchSize;

    public AttemptExpiryWorker(AttemptExpiryService attemptExpiryService) {
        this.attemptExpiryService = attemptExpiryService;
    }

    @Scheduled(fixedDelayString = "${app.delivery.expiry-worker.interval:PT30S}",
            initialDelayString = "${app.delivery.expiry-worker.initial-delay:PT20S}")
    public void submitExpiredAttempts() {
        int submitted = attemptExpiryService.sweep(batchSize);
        if (submitted > 0) {
            log.info("Expiry worker auto-submitted {} expired attempt(s)", submitted);
        }
    }
}
