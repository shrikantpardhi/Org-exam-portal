package com.company.exam.identity.exception;

import com.company.exam.common.api.ApiError;
import com.company.exam.common.exception.ApiException;
import com.company.exam.common.exception.ErrorCode;
import java.util.List;

/**
 * The operation would remove the last active administrator
 * ({@code specs/10-P1-Identity-V1.5.md §63}: 409 {@code LAST_ADMIN}).
 */
public class LastAdminException extends ApiException {

    private static final String MESSAGE = "Removing the last active administrator is blocked";

    public LastAdminException() {
        super(ErrorCode.CONFLICT, MESSAGE, List.of(ApiError.of("LAST_ADMIN", MESSAGE)));
    }
}
