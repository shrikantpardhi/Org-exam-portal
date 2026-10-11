package com.company.exam.delivery.exception;

import com.company.exam.common.api.ApiError;
import com.company.exam.common.exception.ApiException;
import com.company.exam.common.exception.ErrorCode;
import java.util.List;

/**
 * Start Exam was requested before the attempt snapshot reached READY
 * ({@code specs/14-P5-Delivery-V1.5.md §29}). HTTP 409 with error code {@code PREPARATION_NOT_READY}.
 *
 * <p>The backend never falls back to synchronous snapshot generation.
 */
public class PreparationNotReadyException extends ApiException {

    public PreparationNotReadyException() {
        super(ErrorCode.CONFLICT, "Assessment preparation is not complete",
                List.of(ApiError.of("snapshotStatus", "PREPARATION_NOT_READY",
                        "Please retry when the prepared attempt is ready")));
    }
}
