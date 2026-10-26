package com.company.exam.identity.exception;

import com.company.exam.common.api.ApiError;
import com.company.exam.common.exception.ApiException;
import com.company.exam.common.exception.ErrorCode;
import java.util.List;

/**
 * A caller attempted to put a contract-reserved permission onto a role it may not touch
 * ({@code 20-P11-Administration-V1.5.md} §17, ADR-0039, D-03): 403 {@code RESERVED_PERMISSION_DENIED}.
 *
 * <p>Reserved codes are declared as data ({@code RESERVED_PERMISSIONS} business rule) and the caller
 * must hold the rule's bypass permission to place one. A missing, non-ACTIVE, or malformed rule
 * fails closed: every permission is then treated as reserved and nothing can be assigned (G-10).
 */
public class ReservedPermissionException extends ApiException {

    private static final String MESSAGE = "This permission is reserved and cannot be assigned to a role";

    public ReservedPermissionException() {
        super(ErrorCode.AUTHORIZATION_ERROR, MESSAGE,
                List.of(ApiError.of("RESERVED_PERMISSION_DENIED", MESSAGE)));
    }

    public ReservedPermissionException(String message) {
        super(ErrorCode.AUTHORIZATION_ERROR, message,
                List.of(ApiError.of("RESERVED_PERMISSION_DENIED", message)));
    }
}
