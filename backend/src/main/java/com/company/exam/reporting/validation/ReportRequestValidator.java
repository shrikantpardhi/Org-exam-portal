package com.company.exam.reporting.validation;

import com.company.exam.common.api.ApiError;
import com.company.exam.common.exception.ValidationException;
import com.company.exam.reporting.catalog.ReportFormat;
import com.company.exam.reporting.catalog.ReportType;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Report request validation ({@code specs/22-P13-Reporting-V1.5.md §15, §16, §105}).
 *
 * <p>Filters are untrusted input. Validation confirms the report type exists and is enabled, the
 * format is supported, every filter key is in the report's schema, UUIDs and dates are well formed,
 * and the date range is ordered and within the configured maximum interval — all <em>before</em> any
 * expensive query runs. Raw filter text is never interpolated anywhere (§105, §106).
 */
@Component
public class ReportRequestValidator {

    private final Duration maxInterval;

    public ReportRequestValidator(@Value("${app.reporting.max-interval:P366D}") Duration maxInterval) {
        this.maxInterval = maxInterval;
    }

    /** Resolves and validates the report type, returning the catalog entry to execute. */
    public ReportType resolveType(String reportType) {
        ReportType type = ReportType.byCode(reportType).orElseThrow(() -> new ValidationException(
                "Unsupported report type",
                List.of(ApiError.of("reportType", "REPORT_TYPE_UNKNOWN",
                        "No report type is registered with that identifier"))));
        if (!type.enabled()) {
            throw new ValidationException("Report type is not available",
                    List.of(ApiError.of("reportType", "REPORT_NOT_AVAILABLE",
                            "That report type is registered but its data provider is not implemented")));
        }
        return type;
    }

    public ReportFormat resolveFormat(ReportType type, String format) {
        ReportFormat requested;
        try {
            requested = ReportFormat.valueOf(format == null ? "" : format.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            throw new ValidationException("Unsupported report format",
                    List.of(ApiError.of("format", "REPORT_FORMAT_UNSUPPORTED",
                            "Only PDF and CSV are supported")));
        }
        if (!type.supportedFormats().contains(requested)) {
            throw new ValidationException("Format is not supported for this report",
                    List.of(ApiError.of("format", "REPORT_FORMAT_UNSUPPORTED",
                            "That report does not support the requested format")));
        }
        return requested;
    }

    /** Validates filter keys, then the value shapes, then the date range ({@code §15, §16}). */
    public void validateFilters(ReportType type, Map<String, String> filters) {
        Map<String, String> supplied = filters == null ? Map.of() : filters;
        List<ApiError> errors = new ArrayList<>();
        for (String key : supplied.keySet()) {
            if (!type.filterSchema().contains(key)) {
                errors.add(ApiError.of(key, "REPORT_FILTER_UNSUPPORTED",
                        "That filter is not part of this report's schema"));
            }
        }
        for (Map.Entry<String, String> entry : supplied.entrySet()) {
            if (!type.filterSchema().contains(entry.getKey())) {
                continue;
            }
            String value = entry.getValue();
            if (value == null || value.isBlank()) {
                continue;
            }
            if (entry.getKey().endsWith("Id")) {
                try {
                    UUID.fromString(value.trim());
                } catch (IllegalArgumentException notAnId) {
                    errors.add(ApiError.of(entry.getKey(), "REPORT_FILTER_INVALID_UUID",
                            "That filter must be a UUID"));
                }
            }
            if (entry.getKey().equals("from") || entry.getKey().equals("to")) {
                try {
                    Instant.parse(value.trim());
                } catch (DateTimeParseException notAnInstant) {
                    errors.add(ApiError.of(entry.getKey(), "REPORT_FILTER_INVALID_DATE",
                            "That filter must be an ISO-8601 instant"));
                }
            }
        }
        errors.addAll(validateRange(supplied));
        if (!errors.isEmpty()) {
            throw new ValidationException("Report request validation failed", errors);
        }
    }

    /**
     * Date-range rules ({@code §16}): {@code from} and {@code to} are both inclusive UTC instants,
     * {@code to} must not precede {@code from}, and the interval must not exceed the maximum.
     */
    private List<ApiError> validateRange(Map<String, String> filters) {
        Instant from = parse(filters.get("from"));
        Instant to = parse(filters.get("to"));
        List<ApiError> errors = new ArrayList<>();
        if (from != null && to != null) {
            if (to.isBefore(from)) {
                errors.add(ApiError.of("to", "REPORT_DATE_RANGE_INVALID", "to must not precede from"));
            } else if (Duration.between(from, to).compareTo(maxInterval) > 0) {
                errors.add(ApiError.of("to", "REPORT_DATE_RANGE_TOO_LARGE",
                        "The requested interval exceeds the configured maximum"));
            }
        }
        return errors;
    }

    private Instant parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value.trim());
        } catch (DateTimeParseException invalid) {
            return null;
        }
    }
}
