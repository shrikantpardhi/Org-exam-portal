package com.company.exam.reporting.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Reporting request and response payloads ({@code specs/22-P13-Reporting-V1.5.md §14, §24, §28, §29}).
 *
 * <p>The request shape is frozen: {@code reportType}, {@code filters}, {@code format}. There is
 * deliberately no field that accepts SQL, a table name, or a column expression (§106).
 */
public final class ReportingDtos {

    private ReportingDtos() {
    }

    @Schema(name = "ReportRequest")
    public record ReportRequest(@NotBlank String reportType, Map<String, String> filters,
            @NotBlank String format) {
    }

    /** A catalog entry, so a client discovers report types rather than guessing them (§11). */
    public record ReportCatalogEntry(String reportType, String category, String displayName, String description,
            List<String> supportedFormats, String permission, List<String> filterSchema, String executionMode,
            boolean enabled) {
    }

    /** The synchronous result for a small bounded report ({@code §5}). */
    public record ReportResult(String reportType, String format, int rowCount, String artifactName,
            String downloadUrl, List<Map<String, String>> rows, List<MetricView> metrics, String freshnessNote) {
    }

    /** An executive metric always carries its definition (§70). */
    public record MetricView(String name, String value, String definition) {
    }

    /** 202 payload for a queued large report ({@code §24}). */
    public record ReportAccepted(UUID jobId, String status) {
    }

    /** Job status with safe fields only — no stack traces or infrastructure identifiers (§28). */
    public record ReportJobStatus(UUID jobId, String reportType, String status, String format, String errorCode,
            String errorMessage, String artifactName, String downloadUrl, Instant queuedOn, Instant completedOn) {
    }

    /** One row of the report history, served from {@code background_job} ({@code §29, §30}). */
    public record ReportHistoryEntry(UUID jobId, String reportName, UUID generatedBy, Instant generatedOn,
            String status, String downloadUrl) {
    }
}
