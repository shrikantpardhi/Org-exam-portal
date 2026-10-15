package com.company.exam.evaluation.exception;

import com.company.exam.common.exception.ErrorCode;

/** Re-evaluation is not allowed in the current state ({@code §46, §173}). */
public class ReEvaluationNotAllowedException extends EvaluationException {

    public ReEvaluationNotAllowedException(String message) {
        super(ErrorCode.CONFLICT, "attemptId", "RE_EVALUATION_NOT_ALLOWED", message);
    }
}
