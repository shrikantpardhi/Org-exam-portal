package com.company.exam.common.exception;

import com.company.exam.common.api.ApiError;
import java.util.List;

/** Business-rule validation failure (HTTP 422). */
public class BusinessException extends ApiException {

    public BusinessException(String message) {
        super(ErrorCode.BUSINESS_ERROR, message);
    }

    public BusinessException(String message, List<ApiError> errors) {
        super(ErrorCode.BUSINESS_ERROR, message, errors);
    }
}
