package com.company.exam.common.exception;

/** An unexpected server-side failure (HTTP 500). Details are logged, never returned. */
public class SystemException extends ApiException {

    public SystemException(String message, Throwable cause) {
        super(ErrorCode.SYSTEM_ERROR, message, cause);
    }
}
