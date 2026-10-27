package com.company.exam.identity.exception;

import com.company.exam.common.api.ApiError;
import com.company.exam.common.exception.ApiException;
import com.company.exam.common.exception.ErrorCode;
import java.util.List;

/**
 * A non-primary administrator attempted to change another user's roles (ADR-0052).
 *
 * <p>403 {@code PRIMARY_ADMIN_REQUIRED}, distinct from {@code SELF_ROLE_MODIFICATION} so the caller
 * can tell "you cannot edit yourself" from "you are not the primary administrator" — the two need
 * different remedies.
 *
 * <p>Also raised when the organization has no designated primary, because role editing is then
 * refused for everyone in it (the fail-closed rule).
 */
public class PrimaryAdminRequiredException extends ApiException {

    private static final String CODE = "PRIMARY_ADMIN_REQUIRED";

    public PrimaryAdminRequiredException(String message) {
        super(ErrorCode.AUTHORIZATION_ERROR, message, List.of(ApiError.of(CODE, message)));
    }
}
