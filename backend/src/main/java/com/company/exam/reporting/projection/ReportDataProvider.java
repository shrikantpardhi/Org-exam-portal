package com.company.exam.reporting.projection;

/**
 * A report's projection contract ({@code specs/22-P13-Reporting-V1.5.md §33}).
 *
 * <p>Each implemented report type has exactly one provider. The provider reaches source data through
 * the owning module's read contract — never through another module's internal repository (§10).
 *
 * <p>Providers are strictly read-only: a report never repairs a result, changes a payment, publishes
 * an assessment, changes an answer key, activates a user, or modifies a notification (§7.3).
 */
public interface ReportDataProvider {

    /** The report type this provider implements. */
    com.company.exam.reporting.catalog.ReportType reportType();

    ReportProjection project(ReportCriteria criteria);
}
