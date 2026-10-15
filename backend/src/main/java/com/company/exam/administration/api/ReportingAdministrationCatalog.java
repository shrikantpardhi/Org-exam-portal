package com.company.exam.administration.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The Administration read contract consumed by Reporting
 * ({@code specs/22-P13-Reporting-V1.5.md §10, §64-§68, §71}).
 *
 * <p>Reporting reads administration-owned facts through this contract and never through the
 * {@code audit_log}, {@code lookup} or {@code business_rule} repositories directly, so reporting stays
 * decoupled from the persistence layout (§10).
 *
 * <p>Every method is read-only: a report never changes configuration, rules, lookups or audit
 * ({@code §7.3}). Counts are capped by the caller's {@code limit} so a report can never pull the whole
 * audit table into memory (§7.6).
 */
public interface ReportingAdministrationCatalog {

    /** One audit-trail row, projected to the fields an operations report needs. */
    record AuditRow(UUID id, UUID userId, String entityName, String action, String remarks, Instant createdOn) {
    }

    /** One system-health component row captured at report-generation time ({@code §67}). */
    record HealthRow(String component, String status, Long latencyMs, String message) {
    }

    /**
     * One organization KPI.
     *
     * <p>{@code definition} is mandatory: an executive metric without an explicit definition is
     * semantically ambiguous and is not reported ({@code §70}).
     */
    record KpiRow(String name, long value, String definition) {
    }

    List<AuditRow> auditRows(String entityName, String action, Instant from, Instant to, int limit);

    List<HealthRow> healthSnapshot();

    /** Organization-level KPIs, each with its definition and source noted (§70, §71). */
    List<KpiRow> organizationKpis();
}
