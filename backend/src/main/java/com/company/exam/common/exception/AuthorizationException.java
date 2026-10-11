package com.company.exam.common.exception;

/** The caller is authenticated but not permitted to perform the operation (HTTP 403). */
public class AuthorizationException extends ApiException {

    public AuthorizationException(String message) {
        super(ErrorCode.AUTHORIZATION_ERROR, message);
    }
}
