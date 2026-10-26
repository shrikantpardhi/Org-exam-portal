package com.company.exam.identity.exception;

import com.company.exam.common.api.ApiError;
import com.company.exam.common.exception.ApiException;
import com.company.exam.common.exception.ErrorCode;
import java.util.List;

/**
 * A mutation targets the SUPER_ADMIN account, which cannot be demoted or archived
 * (ADR-0032, 20-P11 §18): 409 {@code SUPER_ADMIN_IMMUTABLE}.
 */
public class SuperAdminImmutabilityException extends ApiException {

    private static final String MESSAGE = "The SUPER_ADMIN account cannot be modified";

    public SuperAdminImmutabilityException() {
        super(ErrorCode.CONFLICT, MESSAGE, List.of(ApiError.of("SUPER_ADMIN_IMMUTABLE", MESSAGE)));
    }
}
