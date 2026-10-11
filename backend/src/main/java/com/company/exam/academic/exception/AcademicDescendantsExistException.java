package com.company.exam.academic.exception;

import com.company.exam.common.api.ApiError;
import com.company.exam.common.exception.ApiException;
import com.company.exam.common.exception.ErrorCode;
import java.util.List;

/**
 * Archiving a hierarchy node was rejected because it still has non-archived descendants
 * ({@code specs/11-P2-Academic-V1.5.md §14}; HTTP 409). P2 does not silently cascade archives.
 */
public class AcademicDescendantsExistException extends ApiException {

    public AcademicDescendantsExistException(String message) {
        super(ErrorCode.CONFLICT, message, List.of(ApiError.of("ACADEMIC_DESCENDANTS_EXIST", message)));
    }
}
