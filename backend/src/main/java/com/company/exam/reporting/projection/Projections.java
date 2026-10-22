package com.company.exam.reporting.projection;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Shared projection helpers ({@code specs/22-P13-Reporting-V1.5.md §33, §34, §91}).
 *
 * <p>Every provider builds explicit rows through these helpers, so no business entity is ever
 * serialized into a report (§34). Values are rendered as stable, locale-independent strings.
 */
final class Projections {

    private Projections() {
    }

    /** Rows are capped per report so a projection can never exhaust memory ({@code §7.6}). */
    static final int MAX_ROWS = 2000;

    static Map<String, String> row(Object... keyValues) {
        Map<String, String> row = new java.util.LinkedHashMap<>();
        for (int index = 0; index < keyValues.length; index += 2) {
            Object value = keyValues[index + 1];
            row.put(String.valueOf(keyValues[index]), value == null ? "" : String.valueOf(value));
        }
        return row;
    }

    static List<ReportProjection.Column> columns(String... keyLabelPairs) {
        List<ReportProjection.Column> columns = new java.util.ArrayList<>();
        for (int index = 0; index < keyLabelPairs.length; index += 2) {
            columns.add(new ReportProjection.Column(keyLabelPairs[index], keyLabelPairs[index + 1]));
        }
        return List.copyOf(columns);
    }

    static String period(Map<String, String> filters) {
        String from = filters == null ? null : filters.get("from");
        String to = filters == null ? null : filters.get("to");
        if (from == null && to == null) {
            return "All time";
        }
        return (from == null ? "start" : from) + " to " + (to == null ? "now" : to);
    }

    static Map<String, String> appliedFilters(Map<String, String> filters) {
        return filters == null ? Map.of() : Map.copyOf(filters);
    }

    /** A stable two-decimal rendering, or an empty cell when the source value is absent. */
    static String decimal(BigDecimal value) {
        return value == null ? "" : value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    /** A ratio rendered as a decimal, or empty when the denominator is zero or unknown. */
    static String ratio(long numerator, long denominator) {
        if (denominator <= 0) {
            return "";
        }
        return BigDecimal.valueOf(numerator)
                .divide(BigDecimal.valueOf(denominator), 4, RoundingMode.HALF_UP)
                .toPlainString();
    }

    static String text(Instant value) {
        return value == null ? "" : value.toString();
    }

    static String text(UUID value) {
        return value == null ? "" : value.toString();
    }

    static ReportProjection empty(String title, ReportCriteria criteria) {
        return ReportProjection.empty(title, period(criteria.filters()), appliedFilters(criteria.filters()),
                criteria.generatedAt(), criteria.callerId());
    }

    static long count(List<?> values) {
        return values == null ? 0L : values.size();
    }
}
