package com.company.exam.evaluation.exception;

import com.company.exam.common.exception.ErrorCode;

/** The result cannot be published in its current state ({@code §54, §123}). */
public class ResultNotPublishableException extends EvaluationException {

    public ResultNotPublishableException(String message) {
        super(ErrorCode.CONFLICT, "resultId", "RESULT_NOT_PUBLISHABLE", message);
    }
}
