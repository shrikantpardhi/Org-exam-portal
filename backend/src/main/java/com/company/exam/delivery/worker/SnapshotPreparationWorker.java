package com.company.exam.delivery.worker;

import com.company.exam.administration.api.BusinessRuleResolver;
import com.company.exam.delivery.application.SnapshotPreparationService;
import com.company.exam.delivery.observability.DeliveryMetrics;
import java.math.BigDecimal;
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
 * atomically, reclaims attempts whose PREPARING worker died, retries FAILED attempts on a bounded
 * sweep, and never relies on an in-process lock. Disabled in tests via
 * {@code app.delivery.worker.enabled=false}.
 */
@Component
@ConditionalOnProperty(name = "app.delivery.worker.enabled", havingValue = "true")
public class SnapshotPreparationWorker {

    private static final Logger log = LoggerFactory.getLogger(SnapshotPreparationWorker.class);

    /**
     * The business rule that gates and tunes the FAILED-snapshot retry sweep (GAP-157).
     *
     * <p>Administration owns persistence, Delivery owns the semantics (P11 §88). The key avoids every
     * fragment the rule validator rejects, so it is storable as data.
     */
    static final String RETRY_SWEEP_RULE = "DELIVERY_SNAPSHOT_RETRY_SWEEP";

    /**
     * Documented safe default when the rule is missing, non-ACTIVE or malformed (P11 §9.4).
     *
     * <p>Disabled. A retry sweep re-runs snapshot materialization against real published content, so
     * enabling it is an operational decision that must be recorded as rule data — not something that
     * silently starts happening because a rule row is absent.
     */
    private static final boolean DEFAULT_ENABLED = false;

    private static final Duration DEFAULT_COOL_DOWN = Duration.ofMinutes(10);
    private static final Duration DEFAULT_MAX_AGE = Duration.ofHours(24);
    private static final int DEFAULT_SWEEP_LIMIT = 20;

    private final SnapshotPreparationService preparationService;
    private final DeliveryMetrics metrics;
    private final BusinessRuleResolver businessRules;

    @Value("${app.delivery.worker.batch-size:50}")
    private int batchSize;

    @Value("${app.delivery.worker.stale-timeout:PT5M}")
    private Duration staleTimeout;

    public SnapshotPreparationWorker(SnapshotPreparationService preparationService, DeliveryMetrics metrics,
            BusinessRuleResolver businessRules) {
        this.preparationService = preparationService;
        this.metrics = metrics;
        this.businessRules = businessRules;
    }

    @Scheduled(fixedDelayString = "${app.delivery.worker.interval:PT30S}",
            initialDelayString = "${app.delivery.worker.initial-delay:PT15S}")
    public void preparePendingAttempts() {
        int reclaimed = preparationService.reclaimStale(staleTimeout);
        int prepared = preparationService.preparePendingBatch(batchSize);
        int retried = retryFailedSnapshots();
        // The pending backlog drives the SnapshotBacklogHigh alert (P12 §113).
        metrics.snapshotBacklog((int) preparationService.pendingSnapshotCount());
        if (prepared > 0 || reclaimed > 0 || retried > 0) {
            log.info("Snapshot worker prepared={} reclaimedStale={} retried={}", prepared, reclaimed, retried);
        }
    }

    /**
     * The bounded retry sweep for FAILED snapshots ({@code §23}, GAP-157).
     *
     * <p>A failure nobody retried left the candidate permanently unable to start. Unlike
     * {@code preparePendingBatch}, which serves first-time preparation, this only ever touches rows
     * that are already FAILED, and only inside the configured cool-down and age window.
     *
     * <p>Returns 0 when the rule is absent or not ACTIVE — the documented safe default.
     */
    int retryFailedSnapshots() {
        var rule = businessRules.resolve(RETRY_SWEEP_RULE).orElse(null);
        if (rule == null || !rule.bool("enabled").orElse(DEFAULT_ENABLED)) {
            return 0;
        }
        Duration coolDown = duration(rule.decimal("coolDownMinutes"), DEFAULT_COOL_DOWN);
        Duration maxAge = duration(rule.decimal("maxAgeHours"), DEFAULT_MAX_AGE);
        int limit = rule.decimal("batchSize").map(BigDecimal::intValue).orElse(DEFAULT_SWEEP_LIMIT);
        if (limit <= 0) {
            limit = DEFAULT_SWEEP_LIMIT;
        }
        return preparationService.retryFailedSweep(limit, coolDown, maxAge);
    }

    private static Duration duration(java.util.Optional<BigDecimal> minutes, Duration fallback) {
        if (minutes.isEmpty()) {
            return fallback;
        }
        BigDecimal value = minutes.get();
        return value.signum() <= 0 ? fallback : Duration.ofSeconds(value.multiply(BigDecimal.valueOf(60)).longValue());
    }
}
