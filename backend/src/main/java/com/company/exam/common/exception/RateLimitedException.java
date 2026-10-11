package com.company.exam.common.exception;

/** The caller exceeded a rate limit (HTTP 429). */
public class RateLimitedException extends ApiException {

    public RateLimitedException(String message) {
        super(ErrorCode.RATE_LIMITED, message);
    }
}
