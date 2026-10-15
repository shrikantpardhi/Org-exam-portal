package com.company.exam.evaluation.exception;

import com.company.exam.common.exception.ErrorCode;

/** The selected answer key is not published ({@code specs/15-P6-Evaluation-V1.5.md §42, §48}). */
public class AnswerKeyNotPublishedException extends EvaluationException {

    public AnswerKeyNotPublishedException(String message) {
        super(ErrorCode.CONFLICT, "answerKeyId", "ANSWER_KEY_NOT_PUBLISHED", message);
    }
}
