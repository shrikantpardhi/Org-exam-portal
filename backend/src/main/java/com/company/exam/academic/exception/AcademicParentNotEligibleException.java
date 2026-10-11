package com.company.exam.academic.exception;

import com.company.exam.common.api.ApiError;
import com.company.exam.common.exception.ApiException;
import com.company.exam.common.exception.ErrorCode;
import java.util.List;

/**
 * A child record was requested under a parent that is not eligible for new children
 * ({@code specs/11-P2-Academic-V1.5.md §13}; HTTP 409).
 */
public class AcademicParentNotEligibleException extends ApiException {

    public AcademicParentNotEligibleException(String message) {
        super(ErrorCode.INVALID_STATE, message, List.of(ApiError.of("PARENT_NOT_ELIGIBLE", message)));
    }
}
