package com.company.exam.reporting.projection;

import com.company.exam.administration.api.ReportingAdministrationCatalog;
import com.company.exam.identity.api.AdminUserDirectory;
import com.company.exam.reporting.catalog.ReportType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * The implemented report projections ({@code specs/22-P13-Reporting-V1.5.md §37-§71}).
 *
 * <p>Each provider composes source data through the owning module's read contract and maps it to
 * explicit columns and rows. No business entity is serialized into a report ({@code §34}), every
 * query is bounded, and no provider mutates anything ({@code §7.3, §7.6}).
 *
 * <p>The report families that need a read contract the owning module does not expose yet remain
 * registered in the catalog with {@code enabled=false} and are tracked in {@code docs/known-gaps.md};
 * they fail with a controlled {@code REPORT_NOT_AVAILABLE} rather than returning a misleading
 * artifact.
 */
public final class OperationsReportProviders {

    private OperationsReportProviders() {
    }

    /** Rows are capped per report so a projection can never exhaust memory ({@code §7.6}). */
    static final int MAX_ROWS = 2000;

    private static Map<String, String> row(Object... keyValues) {
        Map<String, String> row = new LinkedHashMap<>();
        for (int index = 0; index < keyValues.length; index += 2) {
            Object value = keyValues[index + 1];
            row.put(String.valueOf(keyValues[index]), value == null ? "" : String.valueOf(value));
        }
        return row;
    }

    private static String period(Map<String, String> filters) {
        String from = filters == null ? null : filters.get("from");
        String to = filters == null ? null : filters.get("to");
        if (from == null && to == null) {
            return "All time";
        }
        return (from == null ? "start" : from) + " to " + (to == null ? "now" : to);
    }

    private static Map<String, String> appliedFilters(Map<String, String> filters) {
        return filters == null ? Map.of() : Map.copyOf(filters);
    }

    // ------------------------------------------------------------------ Operations

    /** OPERATIONS_USER_ACTIVITY — identity-owned user list, masked to safe columns (§64). */
    @Component
    public static class UserActivityProvider implements ReportDataProvider {

        private final AdminUserDirectory userDirectory;

        public UserActivityProvider(AdminUserDirectory userDirectory) {
            this.userDirectory = userDirectory;
        }

        @Override
        public ReportType reportType() {
            return ReportType.OPERATIONS_USER_ACTIVITY;
        }

        @Override
        public ReportProjection project(ReportCriteria criteria) {
            String query = criteria.string("query");
            List<AdminUserDirectory.AdminUserView> users = query == null || query.isBlank()
                    ? List.of()
                    : userDirectory.search(query, MAX_ROWS);
            List<Map<String, String>> rows = new ArrayList<>();
            for (AdminUserDirectory.AdminUserView user : users) {
                rows.add(row("userId", user.userId(), "firstName", user.firstName(), "lastName", user.lastName(),
                        // Contact data is masked in the report exactly as in the support console (P11 §35).
                        "email", maskEmail(user.email()), "status", user.status()));
            }
            return new ReportProjection(ReportType.OPERATIONS_USER_ACTIVITY.displayName(), "All time",
                    appliedFilters(criteria.filters()), criteria.generatedAt(), criteria.callerId(),
                    List.of(new ReportProjection.Column("userId", "User id"),
                            new ReportProjection.Column("firstName", "First name"),
                            new ReportProjection.Column("lastName", "Last name"),
                            new ReportProjection.Column("email", "Email (masked)"),
                            new ReportProjection.Column("status", "Status")),
                    rows, List.of(), "Contact values are masked; identity is owned by the Identity module.");
        }

        private String maskEmail(String email) {
            if (email == null || email.isBlank()) {
                return "";
            }
            int at = email.indexOf('@');
            return at <= 0 ? "***" : email.charAt(0) + "***" + email.substring(at);
        }
    }

    /** OPERATIONS_AUDIT_ACTIVITY — the administration-owned audit trail (§65). */
    @Component
    public static class AuditActivityProvider implements ReportDataProvider {

        private final ReportingAdministrationCatalog administration;

        public AuditActivityProvider(ReportingAdministrationCatalog administration) {
            this.administration = administration;
        }

        @Override
        public ReportType reportType() {
            return ReportType.OPERATIONS_AUDIT_ACTIVITY;
        }

        @Override
        public ReportProjection project(ReportCriteria criteria) {
            List<ReportingAdministrationCatalog.AuditRow> entries = administration.auditRows(
                    criteria.string("entityName"), criteria.string("action"), criteria.instant("from"),
                    criteria.instant("to"), MAX_ROWS);
            List<Map<String, String>> rows = new ArrayList<>();
            for (ReportingAdministrationCatalog.AuditRow entry : entries) {
                rows.add(row("id", entry.id(), "entityName", entry.entityName(), "entityId", "", "action",
                        entry.action(), "userId", entry.userId(), "createdOn", entry.createdOn(), "remarks",
                        entry.remarks()));
            }
            return new ReportProjection(ReportType.OPERATIONS_AUDIT_ACTIVITY.displayName(),
                    period(criteria.filters()), appliedFilters(criteria.filters()), criteria.generatedAt(),
                    criteria.callerId(),
                    List.of(new ReportProjection.Column("id", "Audit id"),
                            new ReportProjection.Column("entityName", "Entity"),
                            new ReportProjection.Column("action", "Action"),
                            new ReportProjection.Column("userId", "Actor"),
                            new ReportProjection.Column("createdOn", "When"),
                            new ReportProjection.Column("remarks", "Remarks")),
                    rows, List.of(), "Audit rows are insert-only and immutable.");
        }
    }

    /** OPERATIONS_SYSTEM_HEALTH — a component snapshot at generation time (§67). */
    @Component
    public static class SystemHealthProvider implements ReportDataProvider {

        private final ReportingAdministrationCatalog administration;

        public SystemHealthProvider(ReportingAdministrationCatalog administration) {
            this.administration = administration;
        }

        @Override
        public ReportType reportType() {
            return ReportType.OPERATIONS_SYSTEM_HEALTH;
        }

        @Override
        public ReportProjection project(ReportCriteria criteria) {
            List<ReportingAdministrationCatalog.HealthRow> components = administration.healthSnapshot();
            List<Map<String, String>> rows = new ArrayList<>();
            for (ReportingAdministrationCatalog.HealthRow component : components) {
                rows.add(row("component", component.component(), "status", component.status(), "latencyMs",
                        component.latencyMs(), "message", component.message()));
            }
            return new ReportProjection(ReportType.OPERATIONS_SYSTEM_HEALTH.displayName(), "Point in time",
                    appliedFilters(criteria.filters()), criteria.generatedAt(), criteria.callerId(),
                    List.of(new ReportProjection.Column("component", "Component"),
                            new ReportProjection.Column("status", "Status"),
                            new ReportProjection.Column("latencyMs", "Latency (ms)"),
                            new ReportProjection.Column("message", "Message")),
                    rows, List.of(), "Health is time-sensitive; this snapshot is not a durable history.");
        }
    }

    // ------------------------------------------------------------------ Executive

    /**
     * EXECUTIVE_KPI_SUMMARY — organization KPIs, each with its explicit definition (§70, §71).
     */
    @Component
    public static class ExecutiveKpiProvider implements ReportDataProvider {

        private final ReportingAdministrationCatalog administration;

        public ExecutiveKpiProvider(ReportingAdministrationCatalog administration) {
            this.administration = administration;
        }

        @Override
        public ReportType reportType() {
            return ReportType.EXECUTIVE_KPI_SUMMARY;
        }

        @Override
        public ReportProjection project(ReportCriteria criteria) {
            List<ReportingAdministrationCatalog.KpiRow> kpis = administration.organizationKpis();
            List<ReportProjection.SummaryMetric> metrics = new ArrayList<>();
            List<Map<String, String>> rows = new ArrayList<>();
            for (ReportingAdministrationCatalog.KpiRow kpi : kpis) {
                metrics.add(new ReportProjection.SummaryMetric(kpi.name(), Long.toString(kpi.value()),
                        kpi.definition()));
                rows.add(row("metric", kpi.name(), "value", kpi.value(), "definition", kpi.definition()));
            }
            return new ReportProjection(ReportType.EXECUTIVE_KPI_SUMMARY.displayName(), "Current",
                    appliedFilters(criteria.filters()), criteria.generatedAt(), criteria.callerId(),
                    List.of(new ReportProjection.Column("metric", "Metric"),
                            new ReportProjection.Column("value", "Value"),
                            new ReportProjection.Column("definition", "Definition")),
                    rows, metrics,
                    "Executive scope is the authorized organization only; student-level data is not reported.");
        }
    }
}
