package com.company.exam.evaluation.exception;

import com.company.exam.common.exception.ErrorCode;

/** Scorecard artifact generation failed ({@code §57, §180}). */
public class ScorecardGenerationFailedException extends EvaluationException {

    public ScorecardGenerationFailedException(String message, Throwable cause) {
        super(ErrorCode.SYSTEM_ERROR, "SCORECARD_GENERATION_FAILED", message, cause);
    }
}
