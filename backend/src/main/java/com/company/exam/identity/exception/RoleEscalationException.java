package com.company.exam.identity.exception;

import com.company.exam.common.api.ApiError;
import com.company.exam.common.exception.ApiException;
import com.company.exam.common.exception.ErrorCode;
import java.util.List;

/**
 * A role is being assigned whose permission set contains a code the assigning caller does not hold
 * (D-08 Rule A, ADR-0044): 403 {@code ROLE_ESCALATION_DETECTED}.
 *
 * <p>Distinct from {@link PermissionNotHeldException} on purpose. That refusal says the caller
 * <em>named</em> a permission it lacks while editing a role; this one says the caller named a role
 * whose <em>existing content</em> it cannot see the full effect of. Keeping them separate is what
 * lets an operator tell "this request was wrong" from "this role is beyond me".
 */
public class RoleEscalationException extends ApiException {

    private static final String MESSAGE =
            "A role cannot be assigned by a user who does not hold all of its permissions";

    public RoleEscalationException() {
        super(ErrorCode.AUTHORIZATION_ERROR, MESSAGE,
                List.of(ApiError.of("ROLE_ESCALATION_DETECTED", MESSAGE)));
    }
}
