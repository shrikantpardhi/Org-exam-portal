package com.company.exam.delivery.exception;

import com.company.exam.common.api.ApiError;
import com.company.exam.common.exception.ApiException;
import com.company.exam.common.exception.ErrorCode;
import java.util.List;

/**
 * The optimistic-concurrency precondition on response save failed
 * ({@code specs/14-P5-Delivery-V1.5.md §43}). HTTP 412 with error code {@code STALE_RESPONSE}.
 */
public class StaleResponseException extends ApiException {

    public StaleResponseException() {
        super(ErrorCode.PRECONDITION_FAILED, "Response was modified by another request",
                List.of(ApiError.of("response", "STALE_RESPONSE",
                        "The stored response is newer than your request.")));
    }
}
