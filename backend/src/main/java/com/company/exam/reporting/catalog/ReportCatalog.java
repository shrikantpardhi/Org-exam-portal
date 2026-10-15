package com.company.exam.reporting.catalog;

import java.util.Arrays;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * The report catalog ({@code specs/22-P13-Reporting-V1.5.md §11, §107}).
 *
 * <p>Exposes the registered report types and their metadata. The registry is code and configuration,
 * never a database table, and it is the only place a report identifier is resolved — a request cannot
 * name a table, a query, or an unregistered report.
 */
@Component
public class ReportCatalog {

    /** Every registered type, in declaration order, for the catalog endpoint. */
    public List<ReportType> all() {
        return Arrays.asList(ReportType.values());
    }

    public List<ReportType> enabled() {
        return all().stream().filter(ReportType::enabled).toList();
    }

    public List<ReportType> byCategory(ReportCategory category) {
        return all().stream().filter(type -> type.category() == category).toList();
    }
}
