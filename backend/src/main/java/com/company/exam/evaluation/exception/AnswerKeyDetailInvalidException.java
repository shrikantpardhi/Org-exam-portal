package com.company.exam.evaluation.exception;

import com.company.exam.common.exception.ErrorCode;

/** An answer key detail violates the frozen NORMAL/BONUS/DROPPED invariants ({@code §5.2, §30, §121}). */
public class AnswerKeyDetailInvalidException extends EvaluationException {

    public AnswerKeyDetailInvalidException(String field, String message) {
        super(ErrorCode.VALIDATION_ERROR, field, "ANSWER_KEY_DETAIL_INVALID", message);
    }
}
