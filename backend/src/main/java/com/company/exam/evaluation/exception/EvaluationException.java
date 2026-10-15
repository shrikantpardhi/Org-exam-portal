package com.company.exam.evaluation.exception;

import com.company.exam.common.api.ApiError;
import com.company.exam.common.exception.ApiException;
import com.company.exam.common.exception.ErrorCode;
import java.util.List;

/**
 * Base class for P6 controlled exceptions.
 *
 * <p>The HTTP status comes from the shared {@link ErrorCode} category; the machine-readable
 * {@code ApiError.code} carries the P6 business code from
 * {@code specs/15-P6-Evaluation-V1.5.md §100} without adding new {@code ErrorCode} constants.
 */
public abstract class EvaluationException extends ApiException {

    protected EvaluationException(ErrorCode status, String errorCode, String message) {
        super(status, message, List.of(ApiError.of(errorCode, message)));
    }

    protected EvaluationException(ErrorCode status, String field, String errorCode, String message) {
        super(status, message, List.of(ApiError.of(field, errorCode, message)));
    }

    protected EvaluationException(ErrorCode status, String errorCode, String message, Throwable cause) {
        super(status, message, List.of(ApiError.of(errorCode, message)), cause);
    }
}
