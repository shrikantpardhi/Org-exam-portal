package com.company.exam.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.time.Instant;
import java.util.List;

/**
 * Standard error envelope.
 *
 * <pre>
 * {
 *   "success": false,
 *   "message": "Validation failed",
 *   "errors": [ { "field": "email", "code": "INVALID_EMAIL", "message": "..." } ],
 *   "timestamp": "2026-10-07T10:00:00Z",
 *   "path": "/api/v1/example",
 *   "requestId": "..."
 * }
 * </pre>
 *
 * <p>Never contains stack traces, SQL, credentials, tokens, or internal paths.
 *
 * <p>See {@code specs/03-Technical-Design-Document-V1.5.md §20} and
 * {@code specs/09-P0-Foundation-V1.5.md §14}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"success", "message", "errors", "timestamp", "path", "requestId"})
public record ApiErrorResponse(
        boolean success,
        String message,
        List<ApiError> errors,
        Instant timestamp,
        String path,
        String requestId) {

    public static ApiErrorResponse of(String message, List<ApiError> errors, String path, String requestId) {
        return new ApiErrorResponse(false, message, errors == null || errors.isEmpty() ? null : List.copyOf(errors),
                Instant.now(), path, requestId);
    }
}
