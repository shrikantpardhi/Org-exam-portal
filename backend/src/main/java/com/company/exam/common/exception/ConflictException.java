package com.company.exam.common.exception;

/** Conflicting state, duplicate resource, or optimistic-lock failure (HTTP 409). */
public class ConflictException extends ApiException {

    public ConflictException(String message) {
        super(ErrorCode.CONFLICT, message);
    }
}
