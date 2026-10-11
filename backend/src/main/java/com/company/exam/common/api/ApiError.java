package com.company.exam.common.api;

/**
 * A single field-level error entry in the standard error envelope.
 *
 * @param field   the offending field, or {@code null} for non-field errors
 * @param code    a stable, machine-readable error code (never a stack trace or SQL)
 * @param message a safe, human-readable explanation
 */
public record ApiError(String field, String code, String message) {

    public static ApiError of(String code, String message) {
        return new ApiError(null, code, message);
    }

    public static ApiError of(String field, String code, String message) {
        return new ApiError(field, code, message);
    }
}
