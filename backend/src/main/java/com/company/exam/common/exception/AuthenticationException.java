package com.company.exam.common.exception;

/** Authentication is required or has failed (HTTP 401). */
public class AuthenticationException extends ApiException {

    public AuthenticationException(String message) {
        super(ErrorCode.AUTHENTICATION_ERROR, message);
    }

    public AuthenticationException(String message, Throwable cause) {
        super(ErrorCode.AUTHENTICATION_ERROR, message, cause);
    }
}
