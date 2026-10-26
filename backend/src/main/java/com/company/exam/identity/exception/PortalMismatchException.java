package com.company.exam.identity.exception;

import com.company.exam.common.api.ApiError;
import com.company.exam.common.exception.ApiException;
import com.company.exam.common.exception.ErrorCode;
import java.util.List;

/**
 * A signed-in account attempted a login portal it does not belong to (ADR-0035):
 * 401 {@code PORTAL_MISMATCH}. The message is deliberately generic and never names the
 * correct portal, so a wrong-portal attempt reveals nothing.
 */
public class PortalMismatchException extends ApiException {

    private static final String MESSAGE = "This account cannot use this portal";

    public PortalMismatchException() {
        super(ErrorCode.AUTHENTICATION_ERROR, MESSAGE, List.of(ApiError.of("PORTAL_MISMATCH", MESSAGE)));
    }
}
