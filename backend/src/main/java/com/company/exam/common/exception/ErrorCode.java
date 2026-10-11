package com.company.exam.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Stable, machine-readable error categories and their default HTTP status mapping.
 *
 * <p>These are the minimum categories required by
 * {@code specs/09-P0-Foundation-V1.5.md §14} and {@code specs/06-Architecture-Freeze-V1.5.md §52}.
 * {@code PRECONDITION_FAILED} (HTTP 412) is included because the frozen API contract requires
 * precondition support for stale-write protection.
 */
public enum ErrorCode {

    /** Invalid request payload or field validation failure. */
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST),

    /** Authentication required or failed. */
    AUTHENTICATION_ERROR(HttpStatus.UNAUTHORIZED),

    /** Authenticated but not permitted. */
    AUTHORIZATION_ERROR(HttpStatus.FORBIDDEN),

    /** Requested resource does not exist. */
    NOT_FOUND(HttpStatus.NOT_FOUND),

    /** Conflicting state, duplicate, or optimistic-lock failure. */
    CONFLICT(HttpStatus.CONFLICT),

    /** Business-rule validation failure. */
    BUSINESS_ERROR(HttpStatus.UNPROCESSABLE_ENTITY),

    /** Operation not allowed in the current lifecycle state. */
    INVALID_STATE(HttpStatus.CONFLICT),

    /** Conditional request precondition failed (for example, stale write). */
    PRECONDITION_FAILED(HttpStatus.PRECONDITION_FAILED),

    /** Caller exceeded a rate limit. */
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS),

    /** HTTP method not supported for the resource. */
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED),

    /** Request content type not supported for the resource. */
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE),

    /** Unexpected server-side failure. */
    SYSTEM_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    /**
     * Maps an HTTP status to the closest documented error category, used when a framework
     * exception (an {@code ErrorResponse}) must be rendered in the standard envelope.
     */
    public static ErrorCode fromStatus(int statusCode) {
        return switch (statusCode) {
            case 400 -> VALIDATION_ERROR;
            case 401 -> AUTHENTICATION_ERROR;
            case 403 -> AUTHORIZATION_ERROR;
            case 404 -> NOT_FOUND;
            case 405 -> METHOD_NOT_ALLOWED;
            case 409 -> CONFLICT;
            case 412 -> PRECONDITION_FAILED;
            case 415 -> UNSUPPORTED_MEDIA_TYPE;
            case 422 -> BUSINESS_ERROR;
            case 429 -> RATE_LIMITED;
            default -> SYSTEM_ERROR;
        };
    }

    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
