package com.company.exam.common.exception;

import com.company.exam.common.api.ApiError;
import java.util.List;

/** Invalid request payload or field validation failure (HTTP 400). */
public class ValidationException extends ApiException {

    public ValidationException(String message) {
        super(ErrorCode.VALIDATION_ERROR, message);
    }

    public ValidationException(String message, List<ApiError> errors) {
        super(ErrorCode.VALIDATION_ERROR, message, errors);
    }
}
