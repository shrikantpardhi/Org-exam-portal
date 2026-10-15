package com.company.exam.reporting.renderer;

import com.company.exam.reporting.projection.ReportProjection;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * CSV rendering ({@code specs/22-P13-Reporting-V1.5.md §20}).
 *
 * <p>Produces deterministic column order, RFC 4180 quoting, normalized line breaks, and
 * <strong>spreadsheet formula-injection mitigation</strong>: any field whose first character could
 * start a formula ({@code =}, {@code +}, {@code -}, {@code @}, tab, or carriage return) is prefixed
 * with a single quote so a spreadsheet treats it as text rather than executing it.
 */
@Component
public class CsvRenderer {

    private static final String FORMULA_PREFIXES = "=+-@\t\r";
    private static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    public byte[] render(ReportProjection projection) {
        StringBuilder csv = new StringBuilder();
        if (projection.columns() != null && !projection.columns().isEmpty()) {
            Set<String> header = new LinkedHashSet<>();
            for (ReportProjection.Column column : projection.columns()) {
                header.add(column.label());
            }
            appendRow(csv, header.toArray(String[]::new));
        }
        if (projection.rows() != null) {
            for (Map<String, String> row : projection.rows()) {
                String[] values = new String[projection.columns().size()];
                for (int index = 0; index < projection.columns().size(); index++) {
                    values[index] = row.get(projection.columns().get(index).key());
                }
                appendRow(csv, values);
            }
        }
        if (projection.metrics() != null && !projection.metrics().isEmpty()) {
            for (ReportProjection.SummaryMetric metric : projection.metrics()) {
                appendRow(csv, new String[] {metric.name(), metric.value()});
            }
        }
        // A UTF-8 BOM keeps non-ASCII content intact when a spreadsheet opens the file.
        byte[] body = csv.toString().getBytes(StandardCharsets.UTF_8);
        byte[] output = new byte[UTF8_BOM.length + body.length];
        System.arraycopy(UTF8_BOM, 0, output, 0, UTF8_BOM.length);
        System.arraycopy(body, 0, output, UTF8_BOM.length, body.length);
        return output;
    }

    private void appendRow(StringBuilder csv, String[] values) {
        for (int index = 0; index < values.length; index++) {
            if (index > 0) {
                csv.append(',');
            }
            csv.append(escape(values[index]));
        }
        csv.append("\r\n");
    }

    /** Quotes when required and neutralizes formula-like prefixes before quoting. */
    String escape(String value) {
        String sanitized = neutralizeFormula(value);
        if (sanitized.indexOf(',') < 0 && sanitized.indexOf('"') < 0 && sanitized.indexOf('\n') < 0
                && sanitized.indexOf('\r') < 0 && sanitized.indexOf('\t') < 0) {
            return sanitized;
        }
        return '"' + sanitized.replace("\"", "\"\"") + '"';
    }

    /** Prefixes a dangerous leading character so a spreadsheet does not evaluate the cell (§20). */
    String neutralizeFormula(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        // Normalize embedded line breaks so a field cannot forge a new record.
        String normalized = value.replace("\r\n", " ").replace('\r', ' ').replace('\n', ' ');
        if (FORMULA_PREFIXES.indexOf(normalized.charAt(0)) >= 0) {
            return "'" + normalized;
        }
        return normalized;
    }
}
