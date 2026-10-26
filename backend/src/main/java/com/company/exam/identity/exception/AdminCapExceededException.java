package com.company.exam.identity.exception;

import com.company.exam.common.api.ApiError;
import com.company.exam.common.exception.ApiException;
import com.company.exam.common.exception.ErrorCode;
import java.util.List;

/**
 * A role change would create a second administrator in an organization, violating the
 * one-ADMIN-per-organization cap (ADR-0032, MAX_ADMINS_PER_ORG):
 * 409 {@code ADMIN_CAP_EXCEEDED}.
 */
public class AdminCapExceededException extends ApiException {

    private static final String MESSAGE = "The organization already has an administrator";

    public AdminCapExceededException() {
        super(ErrorCode.CONFLICT, MESSAGE, List.of(ApiError.of("ADMIN_CAP_EXCEEDED", MESSAGE)));
    }
}
