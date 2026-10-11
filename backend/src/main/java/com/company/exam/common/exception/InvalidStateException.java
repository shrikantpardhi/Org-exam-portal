package com.company.exam.common.exception;

/** The operation is not allowed in the current lifecycle state (HTTP 409). */
public class InvalidStateException extends ApiException {

    public InvalidStateException(String message) {
        super(ErrorCode.INVALID_STATE, message);
    }
}
