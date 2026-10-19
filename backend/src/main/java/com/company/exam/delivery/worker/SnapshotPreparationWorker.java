package com.company.exam.delivery.worker;

import com.company.exam.delivery.application.SnapshotPreparationService;
import com.company.exam.delivery.entity.SnapshotStatus;
import com.company.exam.delivery.observability.DeliveryMetrics;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * The asynchronous snapshot preparation worker ({@code specs/14-P5-Delivery-V1.5.md §20-§23, §101-§103}).
 *
 * <p>It claims PENDING attempts with a database-safe conditional update, materializes their snapshot
 * atomically, reclaims attempts whose PREPARING worker died, and never relies on an in-process lock.
 * Disabled in tests via {@code app.delivery.worker.enabled=false}.
 */
@Component
@ConditionalOnProperty(name = "app.delivery.worker.enabled", havingValue = "true")
public class SnapshotPreparationWorker {

    private static final Logger log = LoggerFactory.getLogger(SnapshotPreparationWorker.class);

    private final SnapshotPreparationService preparationService;
    private final DeliveryMetrics metrics;

    @Value("${app.delivery.worker.batch-size:50}")
    private int batchSize;

    @Value("${app.delivery.worker.stale-timeout:PT5M}")
    private Duration staleTimeout;

    public SnapshotPreparationWorker(SnapshotPreparationService preparationService, DeliveryMetrics metrics) {
        this.preparationService = preparationService;
        this.metrics = metrics;
    }

    @Scheduled(fixedDelayString = "${app.delivery.worker.interval:PT30S}",
            initialDelayString = "${app.delivery.worker.initial-delay:PT15S}")
    public void preparePendingAttempts() {
        int reclaimed = preparationService.reclaimStale(staleTimeout);
        int prepared = preparationService.preparePendingBatch(batchSize);
        // The pending backlog drives the SnapshotBacklogHigh alert (P12 §113).
        metrics.snapshotBacklog((int) preparationService.pendingSnapshotCount());
        if (prepared > 0 || reclaimed > 0) {
            log.info("Snapshot worker prepared={} reclaimedStale={}", prepared, reclaimed);
        }
    }
}
