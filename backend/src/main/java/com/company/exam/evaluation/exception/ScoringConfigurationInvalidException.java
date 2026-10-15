package com.company.exam.evaluation.exception;

import com.company.exam.common.exception.ErrorCode;

/** The scoring configuration is invalid (for example a zero published total) ({@code §34, §42}). */
public class ScoringConfigurationInvalidException extends EvaluationException {

    public ScoringConfigurationInvalidException(String message) {
        super(ErrorCode.BUSINESS_ERROR, "assessmentVersionId", "SCORING_CONFIGURATION_INVALID", message);
    }
}
