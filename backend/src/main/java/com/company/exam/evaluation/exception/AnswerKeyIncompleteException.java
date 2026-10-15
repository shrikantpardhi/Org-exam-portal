package com.company.exam.evaluation.exception;

import com.company.exam.common.exception.ErrorCode;

/** The answer key is missing a detail row for an evaluable question ({@code §29, §42}). */
public class AnswerKeyIncompleteException extends EvaluationException {

    public AnswerKeyIncompleteException(String message) {
        super(ErrorCode.BUSINESS_ERROR, "answerKeyId", "ANSWER_KEY_INCOMPLETE", message);
    }
}
