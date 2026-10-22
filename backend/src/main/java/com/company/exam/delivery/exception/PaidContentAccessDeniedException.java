package com.company.exam.delivery.exception;

import com.company.exam.common.api.ApiError;
import com.company.exam.common.exception.ApiException;
import com.company.exam.common.exception.ErrorCode;
import java.util.List;

/**
 * The student holds no valid AccessGrant for a paid assessment
 * ({@code specs/18-P9-Commerce-V1.5.md §3.2, §16, §28.1};
 * {@code specs/14-P5-Delivery-V1.5.md §19}; ADR-0026).
 *
 * <p>HTTP 403 with error code {@code ACCESS_DENIED_PAID_CONTENT}. The denial is decided on the server
 * from the authenticated principal and persisted state, and it is raised before the attempt transition,
 * so a denied start leaves the attempt in {@code NOT_STARTED} and creates no session.
 */
public class PaidContentAccessDeniedException extends ApiException {

    public PaidContentAccessDeniedException() {
        super(ErrorCode.AUTHORIZATION_ERROR, "Access to this assessment is not granted",
                List.of(ApiError.of("assessment", "ACCESS_DENIED_PAID_CONTENT",
                        "This assessment requires an active access grant.")));
    }
}
