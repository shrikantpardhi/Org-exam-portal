package com.company.exam.identity.exception;

import com.company.exam.common.api.ApiError;
import com.company.exam.common.exception.ApiException;
import com.company.exam.common.exception.ErrorCode;
import java.util.List;

/**
 * A caller attempted to modify their own role's permissions
 * ({@code specs/10-P1-Identity-V1.5.md §63}: 403 {@code SELF_PERMISSION_MODIFICATION}).
 */
public class SelfPermissionModificationException extends ApiException {

    private static final String MESSAGE = "A user cannot modify the permissions of their own role";

    public SelfPermissionModificationException() {
        super(ErrorCode.AUTHORIZATION_ERROR, MESSAGE,
                List.of(ApiError.of("SELF_PERMISSION_MODIFICATION", MESSAGE)));
    }
}
