package com.company.exam.identity.exception;

import com.company.exam.common.api.ApiError;
import com.company.exam.common.exception.ApiException;
import com.company.exam.common.exception.ErrorCode;
import java.util.List;

/**
 * A caller attempted to assign or revoke their own roles
 * ({@code specs/10-P1-Identity-V1.5.md §63}: 403 {@code SELF_ROLE_MODIFICATION}).
 */
public class SelfRoleModificationException extends ApiException {

    private static final String MESSAGE = "A user cannot assign or revoke their own roles";

    public SelfRoleModificationException() {
        super(ErrorCode.AUTHORIZATION_ERROR, MESSAGE, List.of(ApiError.of("SELF_ROLE_MODIFICATION", MESSAGE)));
    }
}
