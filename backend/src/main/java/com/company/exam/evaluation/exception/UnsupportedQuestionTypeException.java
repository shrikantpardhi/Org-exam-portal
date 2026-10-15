package com.company.exam.evaluation.exception;

import com.company.exam.common.exception.ErrorCode;

/** No evaluator strategy exists for the question template ({@code §42, §122, §167}). */
public class UnsupportedQuestionTypeException extends EvaluationException {

    public UnsupportedQuestionTypeException(String message) {
        super(ErrorCode.BUSINESS_ERROR, "questionVersionId", "UNSUPPORTED_QUESTION_TYPE", message);
    }
}
