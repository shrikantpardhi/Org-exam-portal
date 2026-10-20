package com.company.exam.delivery.exception;

import com.company.exam.common.api.ApiError;
import com.company.exam.common.exception.ApiException;
import com.company.exam.common.exception.ErrorCode;
import java.time.Instant;
import java.util.List;

/**
 * The attempt deadline has passed ({@code specs/14-P5-Delivery-V1.5.md §54, §56};
 * {@code specs/08-Exam-Engine-and-CDUI-Contract-V1.5.md §67}).
 *
 * <p>HTTP 409 with error code {@code ATTEMPT_EXPIRED}. The client must stop treating the attempt as
 * active; the server owns the finalization.
 */
public class AttemptExpiredException extends ApiException {

    public AttemptExpiredException(Instant deadline) {
        super(ErrorCode.CONFLICT, "The attempt time has expired",
                List.of(ApiError.of("attempt", "ATTEMPT_EXPIRED",
                        "The attempt deadline has passed and the attempt is no longer active.")));
    }
}
