package com.company.exam.evaluation.exception;

import com.company.exam.common.exception.ErrorCode;

/** A published answer key is immutable; corrections use a new version ({@code §27, §30}). */
public class AnswerKeyImmutableException extends EvaluationException {

    public AnswerKeyImmutableException(String message) {
        super(ErrorCode.CONFLICT, "answerKeyId", "ANSWER_KEY_IMMUTABLE", message);
    }
}
