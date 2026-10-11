package com.company.exam.content.rendering;

import com.company.exam.common.api.ApiError;
import com.company.exam.common.exception.ApiException;
import com.company.exam.common.exception.ErrorCode;
import java.util.List;

/**
 * Raised when canonical content fails validation
 * ({@code specs/12-P3-Content-V1.5.md §58, §59}).
 *
 * <p>HTTP 400 with a structured {@code errors[]} list of {@link ContentErrorCode} entries.
 */
public class ContentValidationException extends ApiException {

    public ContentValidationException(List<ApiError> errors) {
        super(ErrorCode.VALIDATION_ERROR, "Content validation failed", errors);
    }

    public static ContentValidationException of(String field, ContentErrorCode code, String message) {
        return new ContentValidationException(List.of(ApiError.of(field, code.name(), message)));
    }
}
