package com.company.exam.evaluation.exception;

import com.company.exam.common.exception.ErrorCode;

/** The attempt already has a current evaluation ({@code §44, §45}). */
public class EvaluationAlreadyExistsException extends EvaluationException {

    public EvaluationAlreadyExistsException(String message) {
        super(ErrorCode.CONFLICT, "attemptId", "EVALUATION_ALREADY_EXISTS", message);
    }
}
