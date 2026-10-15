package com.company.exam.evaluation.exception;

import com.company.exam.common.exception.ErrorCode;

/** Official evaluation requires a SUBMITTED attempt ({@code specs/15-P6-Evaluation-V1.5.md §43}). */
public class AttemptNotSubmittedException extends EvaluationException {

    public AttemptNotSubmittedException(String message) {
        super(ErrorCode.INVALID_STATE, "attemptId", "ATTEMPT_NOT_SUBMITTED", message);
    }
}
