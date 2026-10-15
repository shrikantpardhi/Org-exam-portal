package com.company.exam.reporting.projection;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The projection a report produces before rendering
 * ({@code specs/22-P13-Reporting-V1.5.md §19, §33}).
 *
 * <p>It contains only the fields the report needs, expressed as explicit columns and rows. Business
 * entities are never serialized into a report (§34).
 */
public record ReportProjection(String title, String period, Map<String, String> appliedFilters,
        Instant generatedAt, UUID generatedBy, List<Column> columns, List<Map<String, String>> rows,
        List<SummaryMetric> metrics, String freshnessNote) {

    /** A stable column key and its presentation label, in deterministic order (§20). */
    public record Column(String key, String label) {
    }

    /**
     * A summary metric.
     *
     * <p>An executive metric must carry its explicit definition, because a KPI without a definition
     * is semantically ambiguous ({@code §70}). For non-executive reports the definition is optional
     * and may be null.
     */
    public record SummaryMetric(String name, String value, String definition) {
    }

    public int rowCount() {
        return rows == null ? 0 : rows.size();
    }

    /** A projection with no matching data is still a successful report ({@code §17}). */
    public static ReportProjection empty(String title, String period, Map<String, String> appliedFilters,
            Instant generatedAt, UUID generatedBy) {
        return new ReportProjection(title, period, appliedFilters, generatedAt, generatedBy, List.of(), List.of(),
                List.of(), "No matching data for the requested filters.");
    }
}
