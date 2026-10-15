package com.company.exam.evaluation.exception;

import com.company.exam.common.exception.ErrorCode;

/** The answer key does not belong to the attempt's assessment version ({@code §48, §92}). */
public class AnswerKeyNotForAssessmentException extends EvaluationException {

    public AnswerKeyNotForAssessmentException(String message) {
        super(ErrorCode.CONFLICT, "answerKeyId", "ANSWER_KEY_NOT_FOR_ASSESSMENT", message);
    }
}
