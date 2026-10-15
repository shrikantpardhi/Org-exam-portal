package com.company.exam.reporting.projection;

import com.company.exam.reporting.catalog.ReportType;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * The criteria handed to a projection provider
 * ({@code specs/22-P13-Reporting-V1.5.md §33, §83}).
 *
 * <p>Filters are already validated and allow-listed against the report's filter schema, so a
 * provider reads typed, known keys only. Raw filter text is never interpolated into SQL (§105, §106).
 */
public record ReportCriteria(ReportType reportType, Map<String, String> filters, UUID callerId,
        boolean callerIsStudent, Instant generatedAt) {

    public String string(String key) {
        return filters == null ? null : filters.get(key);
    }

    public UUID uuid(String key) {
        String value = string(key);
        return value == null || value.isBlank() ? null : UUID.fromString(value);
    }

    public Instant instant(String key) {
        String value = string(key);
        return value == null || value.isBlank() ? null : Instant.parse(value);
    }

    /**
     * The caller identity a student report must be scoped to: a student is always scoped to
     * themselves and cannot filter by another user id (§76).
     */
    public UUID scopedSubject(String key) {
        UUID requested = uuid(key);
        if (callerIsStudent) {
            return callerId;
        }
        return requested == null ? callerId : requested;
    }
}
