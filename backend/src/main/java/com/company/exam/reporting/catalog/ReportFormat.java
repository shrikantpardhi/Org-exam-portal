package com.company.exam.reporting.catalog;

/**
 * Supported report formats ({@code specs/22-P13-Reporting-V1.5.md §18}).
 *
 * <p>P13 supports a format only when it is explicitly implemented and tested. XLSX is deliberately
 * absent until a tested renderer exists.
 */
public enum ReportFormat {
    PDF,
    CSV
}
