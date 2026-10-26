package com.company.exam.identity.exception;

import com.company.exam.common.api.ApiError;
import com.company.exam.common.exception.ApiException;
import com.company.exam.common.exception.ErrorCode;
import java.util.List;

/**
 * A caller attempted to grant a permission it does not itself hold — bounded delegation
 * ({@code 20-P11-Administration-V1.5.md} §17, ADR-0040, D-04 and D-09): 403 {@code PERMISSION_NOT_HELD}.
 *
 * <p>This code is deliberately distinct from {@code RESERVED_PERMISSION_DENIED}: a reserved code is
 * refused for structural reasons (nobody may place it without the bypass), while an unheld code is
 * refused because of the caller's own authority. Tests assert each one individually so a regression
 * in either rule is visible.
 */
public class PermissionNotHeldException extends ApiException {

    private static final String MESSAGE = "A user cannot grant a permission they do not hold";

    public PermissionNotHeldException() {
        super(ErrorCode.AUTHORIZATION_ERROR, MESSAGE,
                List.of(ApiError.of("PERMISSION_NOT_HELD", MESSAGE)));
    }
}
