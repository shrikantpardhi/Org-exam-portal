package com.company.exam.evaluation.exception;

import com.company.exam.common.exception.ErrorCode;

/** The attempt is not in an evaluable state ({@code §42, §92}). */
public class EvaluationNotAllowedException extends EvaluationException {

    public EvaluationNotAllowedException(String message) {
        super(ErrorCode.INVALID_STATE, "attemptId", "EVALUATION_NOT_ALLOWED", message);
    }
}
