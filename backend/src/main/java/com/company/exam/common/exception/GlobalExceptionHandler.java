package com.company.exam.common.exception;

import com.company.exam.common.api.ApiError;
import com.company.exam.common.api.ApiErrorResponse;
import com.company.exam.common.web.RequestContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Single global exception handler for the API.
 *
 * <p>Renders every controlled and unexpected failure into the standard error envelope
 * ({@code specs/09-P0-Foundation-V1.5.md §14, §103}). Unexpected exceptions are logged with
 * their correlation identifier; internal stack traces are never returned to clients.
 *
 * <p>See {@code specs/03-Technical-Design-Document-V1.5.md §22}.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final String GENERIC_ERROR_MESSAGE = "An unexpected error occurred";
    private static final String VALIDATION_MESSAGE = "Validation failed";

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiErrorResponse> handleApiException(ApiException exception, HttpServletRequest request) {
        if (exception.getStatus().is5xxServerError()) {
            log.error("Request failed: errorCode={} path={}", exception.getErrorCode(), request.getRequestURI(),
                    exception);
        } else {
            log.debug("Request rejected: errorCode={} path={} message={}", exception.getErrorCode(),
                    request.getRequestURI(), exception.getMessage());
        }
        return build(exception.getStatus(), exception.getMessage(), exception.getErrors(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodArgumentNotValid(MethodArgumentNotValidException exception,
            HttpServletRequest request) {
        List<ApiError> errors = exception.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> ApiError.of(
                        fieldError.getField(),
                        fieldError.getCode() == null ? "INVALID_VALUE" : fieldError.getCode(),
                        fieldError.getDefaultMessage() == null ? "Value is invalid" : fieldError.getDefaultMessage()))
                .sorted(Comparator.comparing(ApiError::field))
                .toList();
        return build(HttpStatus.BAD_REQUEST, VALIDATION_MESSAGE, errors, request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolation(ConstraintViolationException exception,
            HttpServletRequest request) {
        List<ApiError> errors = exception.getConstraintViolations().stream()
                .map(violation -> ApiError.of(
                        violation.getPropertyPath() == null ? null : violation.getPropertyPath().toString(),
                        "INVALID_VALUE",
                        violation.getMessage()))
                .toList();
        return build(HttpStatus.BAD_REQUEST, VALIDATION_MESSAGE, errors, request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadableBody(HttpMessageNotReadableException exception,
            HttpServletRequest request) {
        log.debug("Malformed request body on path={}", request.getRequestURI());
        return build(HttpStatus.BAD_REQUEST, "Malformed request body", null, request);
    }

    /**
     * A path or query parameter could not be converted to its target type (for example, a malformed
     * UUID) is a client error, not an internal failure.
     */
    @ExceptionHandler(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleTypeMismatch(
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException exception,
            HttpServletRequest request) {
        log.debug("Invalid value for parameter '{}' on path={}", exception.getName(), request.getRequestURI());
        return build(HttpStatus.BAD_REQUEST, "Invalid value for parameter '" + exception.getName() + "'", null,
                request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNoResource(NoResourceFoundException exception,
            HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, "Resource not found", null, request);
    }

    /**
     * Method-security denials ({@code @PreAuthorize}) are thrown during handler invocation and
     * would otherwise be absorbed by the catch-all below. Rendering them here keeps the security
     * response contract identical to the filter-level handlers.
     */
    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(
            org.springframework.security.access.AccessDeniedException exception, HttpServletRequest request) {
        log.debug("Access denied on path={}", request.getRequestURI());
        return build(HttpStatus.FORBIDDEN, "Access denied", null, request);
    }

    @ExceptionHandler(org.springframework.security.core.AuthenticationException.class)
    public ResponseEntity<ApiErrorResponse> handleAuthentication(
            org.springframework.security.core.AuthenticationException exception, HttpServletRequest request) {
        log.debug("Authentication failure on path={}", request.getRequestURI());
        return build(HttpStatus.UNAUTHORIZED, "Authentication required", null, request);
    }

    /**
     * Catch-all for anything not handled above.
     *
     * <p>Framework exceptions that already carry an HTTP status (for example, method not allowed
     * or unsupported media type) keep that status so HTTP semantics remain correct. Everything
     * else becomes a generic 500 without leaking internals.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception exception, HttpServletRequest request) {
        if (exception instanceof ErrorResponse errorResponse) {
            HttpStatus status = HttpStatus.resolve(errorResponse.getStatusCode().value());
            if (status != null && status.is4xxClientError()) {
                log.debug("Framework client error: status={} path={}", status.value(), request.getRequestURI());
                return build(status, status.getReasonPhrase(), null, request);
            }
        }
        log.error("Unhandled exception on path={}", request.getRequestURI(), exception);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, GENERIC_ERROR_MESSAGE, null, request);
    }

    private ResponseEntity<ApiErrorResponse> build(HttpStatus status, String message, List<ApiError> errors,
            HttpServletRequest request) {
        ApiErrorResponse body = ApiErrorResponse.of(message, errors, request.getRequestURI(),
                RequestContext.getRequestId());
        return ResponseEntity.status(status).body(body);
    }
}
