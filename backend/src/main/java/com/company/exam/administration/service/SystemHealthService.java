package com.company.exam.administration.service;

import com.company.exam.administration.dto.AdministrationDtos;
import com.company.exam.common.job.BackgroundJobRepository;
import com.company.exam.common.job.BackgroundJobStatus;
import com.company.exam.common.job.JobTypes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.springframework.stereotype.Service;

/**
 * Aggregated system health for administrators
 * ({@code specs/20-P11-Administration-V1.5.md §55-§62}).
 *
 * <p>Health is presentation and monitoring data, not a new entity ({@code §55, §57}). Independent
 * checks run in parallel with bounded timeouts so a slow provider cannot hang the screen
 * ({@code §61}). Only safe status, latency, and a message are returned — never credentials,
 * connection strings, environment dumps, or stack traces ({@code §58, §87}). HEALTHY is never
 * inferred from missing telemetry ({@code §60}).
 */
@Service
public class SystemHealthService {

    private static final long CHECK_TIMEOUT_SECONDS = 3;

    private final DataSource dataSource;
    private final BackgroundJobRepository jobRepository;
    private final com.company.exam.common.cache.CacheService cache;

    public SystemHealthService(DataSource dataSource, BackgroundJobRepository jobRepository,
            com.company.exam.common.cache.CacheService cache) {
        this.dataSource = dataSource;
        this.jobRepository = jobRepository;
        this.cache = cache;
    }

    public AdministrationDtos.SystemHealthResponse check() {
        List<CompletableFuture<AdministrationDtos.HealthComponent>> checks = new ArrayList<>();
        checks.add(CompletableFuture.supplyAsync(this::application));
        checks.add(CompletableFuture.supplyAsync(this::postgresql));
        checks.add(CompletableFuture.supplyAsync(this::redis));
        checks.add(CompletableFuture.supplyAsync(this::backgroundWorkers));
        // External providers are optional and provider-specific; absent configuration is UNKNOWN (§56, §60).
        checks.add(CompletableFuture.completedFuture(unknown("EMAIL_PROVIDER", "Provider not configured")));
        checks.add(CompletableFuture.completedFuture(unknown("PAYMENT_PROVIDER", "Provider not configured")));
        checks.add(CompletableFuture.completedFuture(unknown("OBJECT_STORAGE", "Not configured in this deployment")));
        checks.add(CompletableFuture.completedFuture(unknown("PGBOUNCER", "Not configured in this deployment")));

        List<AdministrationDtos.HealthComponent> components = checks.stream()
                .map(future -> future.orTimeout(CHECK_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                        .exceptionally(timeout -> unknown("CHECK", "Timed out"))
                        .join())
                .toList();
        return new AdministrationDtos.SystemHealthResponse(overall(components), Instant.now(), components);
    }

    /**
     * Overall status rule table ({@code §59}): any UNAVAILABLE becomes UNAVAILABLE, any DEGRADED
     * becomes DEGRADED, an UNKNOWN alongside otherwise-healthy components is DEGRADED; HEALTHY
     * requires every component to be HEALTHY.
     */
    private String overall(List<AdministrationDtos.HealthComponent> components) {
        boolean anyUnavailable = components.stream().anyMatch(c -> "UNAVAILABLE".equals(c.status()));
        if (anyUnavailable) {
            return "UNAVAILABLE";
        }
        boolean anyDegraded = components.stream()
                .anyMatch(c -> "DEGRADED".equals(c.status()) || "UNKNOWN".equals(c.status()));
        return anyDegraded ? "DEGRADED" : "HEALTHY";
    }

    private AdministrationDtos.HealthComponent application() {
        return new AdministrationDtos.HealthComponent("APPLICATION", "HEALTHY", 0L, null, Map.of());
    }

    private AdministrationDtos.HealthComponent postgresql() {
        long start = System.nanoTime();
        try (java.sql.Connection connection = dataSource.getConnection();
                java.sql.Statement statement = connection.createStatement()) {
            statement.execute("select 1");
            return new AdministrationDtos.HealthComponent("POSTGRESQL", "HEALTHY",
                    millisSince(start), null, Map.of());
        } catch (Exception failure) {
            return new AdministrationDtos.HealthComponent("POSTGRESQL", "UNAVAILABLE", null,
                    "Database check failed", Map.of());
        }
    }

    private AdministrationDtos.HealthComponent redis() {
        long start = System.nanoTime();
        try {
            cache.get("administration:health:probe", String.class);
            return new AdministrationDtos.HealthComponent("REDIS", "HEALTHY", millisSince(start), null, Map.of());
        } catch (Exception failure) {
            // Redis is cache only: a database fallback keeps the application functional (§60).
            return new AdministrationDtos.HealthComponent("REDIS", "DEGRADED", null,
                    "Cache unavailable; PostgreSQL fallback remains authoritative", Map.of());
        }
    }

    private AdministrationDtos.HealthComponent backgroundWorkers() {
        long running = jobRepository.countByStatus(BackgroundJobStatus.RUNNING);
        long queued = jobRepository.countByJobTypeAndStatus(JobTypes.EMAIL_DELIVERY, BackgroundJobStatus.QUEUED);
        Map<String, String> detail = Map.of("running", Long.toString(running), "queuedEmailDeliveries",
                Long.toString(queued));
        return new AdministrationDtos.HealthComponent("BACKGROUND_WORKERS", "HEALTHY", null, null, detail);
    }

    private AdministrationDtos.HealthComponent unknown(String name, String message) {
        return new AdministrationDtos.HealthComponent(name, "UNKNOWN", null, message, Map.of());
    }

    private long millisSince(long startNanos) {
        return Math.max(0, (System.nanoTime() - startNanos) / 1_000_000);
    }
}
