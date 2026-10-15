package com.company.exam.reporting.renderer;

import com.company.exam.reporting.catalog.ReportFormat;
import com.company.exam.reporting.projection.ReportProjection;
import org.springframework.stereotype.Component;

/**
 * Dispatches a projection to the renderer for the requested format
 * ({@code specs/22-P13-Reporting-V1.5.md §18}).
 *
 * <p>Only PDF and CSV are mapped, matching the formats each catalog entry declares.
 */
@Component
public class ReportRenderer {

    private final PdfRenderer pdfRenderer;
    private final CsvRenderer csvRenderer;

    public ReportRenderer(PdfRenderer pdfRenderer, CsvRenderer csvRenderer) {
        this.pdfRenderer = pdfRenderer;
        this.csvRenderer = csvRenderer;
    }

    public byte[] render(ReportProjection projection, ReportFormat format) {
        return switch (format) {
            case PDF -> pdfRenderer.render(projection);
            case CSV -> csvRenderer.render(projection);
        };
    }

    public String contentType(ReportFormat format) {
        return switch (format) {
            case PDF -> "application/pdf";
            case CSV -> "text/csv;charset=UTF-8";
        };
    }

    public String extension(ReportFormat format) {
        return format == ReportFormat.PDF ? ".pdf" : ".csv";
    }
}
