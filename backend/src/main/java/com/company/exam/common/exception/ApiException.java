package com.company.exam.common.exception;

import com.company.exam.common.api.ApiError;
import java.util.List;
import org.springframework.http.HttpStatus;

/**
 * Base class for all controlled application exceptions.
 *
 * <p>Every {@code ApiException} carries an {@link ErrorCode}, which determines the HTTP status.
 * The {@link GlobalExceptionHandler} renders it as the standard error envelope.
 *
 * <p>Messages passed here are safe to return to clients: they must never contain stack traces,
 * SQL, credentials, tokens, or internal filesystem paths.
 */
public class ApiException extends RuntimeException {

    private final ErrorCode errorCode;
    private final transient List<ApiError> errors;

    public ApiException(ErrorCode errorCode, String message) {
        this(errorCode, message, List.of(), null);
    }

    public ApiException(ErrorCode errorCode, String message, List<ApiError> errors) {
        this(errorCode, message, errors, null);
    }

    public ApiException(ErrorCode errorCode, String message, Throwable cause) {
        this(errorCode, message, List.of(), cause);
    }

    public ApiException(ErrorCode errorCode, String message, List<ApiError> errors, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.errors = errors == null ? List.of() : List.copyOf(errors);
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public HttpStatus getStatus() {
        return errorCode.status();
    }

    public List<ApiError> getErrors() {
        return errors;
    }
}
