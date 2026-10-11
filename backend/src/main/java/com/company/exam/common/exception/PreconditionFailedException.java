package com.company.exam.common.exception;

/** A conditional request precondition failed, for example a stale write (HTTP 412). */
public class PreconditionFailedException extends ApiException {

    public PreconditionFailedException(String message) {
        super(ErrorCode.PRECONDITION_FAILED, message);
    }
}
