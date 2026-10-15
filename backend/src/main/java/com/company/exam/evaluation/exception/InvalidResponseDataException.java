package com.company.exam.evaluation.exception;

import com.company.exam.common.exception.ErrorCode;

/** A stored response is invalid for its question; evaluation fails rather than ignoring it ({@code §67}). */
public class InvalidResponseDataException extends EvaluationException {

    public InvalidResponseDataException(String message) {
        super(ErrorCode.BUSINESS_ERROR, "response", "INVALID_RESPONSE_DATA", message);
    }
}
