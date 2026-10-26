package com.company.exam.identity.exception;

import com.company.exam.common.api.ApiError;
import com.company.exam.common.exception.ApiException;
import com.company.exam.common.exception.ErrorCode;
import java.util.List;

/**
 * A mutation targets a role whose shape is fixed by the deployment contract
 * ({@code 20-P11-Administration-V1.5.md} §18, ADR-0032, ADR-0038): 409 {@code ROLE_IMMUTABLE}.
 *
 * <p>Today the immutable set is the {@code SUPER_ADMIN} role: it cannot be renamed, cannot change
 * status, and cannot have its permission set replaced, by <em>any</em> caller including
 * {@code SUPER_ADMIN} itself (D-02). This is deliberately a different code from
 * {@code SUPER_ADMIN_IMMUTABLE}, which guards the SUPER_ADMIN <em>account</em>: a caller can hold a
 * legitimate reason to be refused for one and not the other, and tests must be able to tell them
 * apart.
 */
public class RoleImmutabilityException extends ApiException {

    private static final String MESSAGE = "This role is fixed by the deployment contract and cannot be modified";

    public RoleImmutabilityException() {
        super(ErrorCode.CONFLICT, MESSAGE, List.of(ApiError.of("ROLE_IMMUTABLE", MESSAGE)));
    }
}
