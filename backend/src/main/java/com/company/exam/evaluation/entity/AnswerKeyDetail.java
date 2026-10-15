package com.company.exam.evaluation.entity;

import com.company.exam.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * The authoritative correctness record for one question version in an answer key
 * ({@code specs/15-P6-Evaluation-V1.5.md §5.2, §21}).
 *
 * <p>This is the ONLY correctness source used by evaluation (AGENTS.md rule 19). When
 * {@code awardedMarks} / {@code awardedNegativeMarks} are present they supersede the immutable
 * {@code attempt_question} snapshot values for that question ({@code §21}).
 */
@Entity
@Table(name = "answer_key_detail")
public class AnswerKeyDetail extends BaseEntity {

    @Column(name = "answer_key_id", nullable = false)
    private UUID answerKeyId;

    @Column(name = "question_version_id", nullable = false)
    private UUID questionVersionId;

    @Column(name = "correct_option_id")
    private UUID correctOptionId;

    @Column(name = "correct_answer_text", columnDefinition = "text")
    private String correctAnswerText;

    @Enumerated(EnumType.STRING)
    @Column(name = "question_outcome", nullable = false, length = 20)
    private QuestionOutcome questionOutcome = QuestionOutcome.NORMAL;

    @Column(name = "awarded_marks", precision = 8, scale = 2)
    private BigDecimal awardedMarks;

    @Column(name = "awarded_negative_marks", precision = 8, scale = 2)
    private BigDecimal awardedNegativeMarks;

    @Column(name = "remarks", columnDefinition = "text")
    private String remarks;

    public UUID getAnswerKeyId() {
        return answerKeyId;
    }

    public void setAnswerKeyId(UUID answerKeyId) {
        this.answerKeyId = answerKeyId;
    }

    public UUID getQuestionVersionId() {
        return questionVersionId;
    }

    public void setQuestionVersionId(UUID questionVersionId) {
        this.questionVersionId = questionVersionId;
    }

    public UUID getCorrectOptionId() {
        return correctOptionId;
    }

    public void setCorrectOptionId(UUID correctOptionId) {
        this.correctOptionId = correctOptionId;
    }

    public String getCorrectAnswerText() {
        return correctAnswerText;
    }

    public void setCorrectAnswerText(String correctAnswerText) {
        this.correctAnswerText = correctAnswerText;
    }

    public QuestionOutcome getQuestionOutcome() {
        return questionOutcome;
    }

    public void setQuestionOutcome(QuestionOutcome questionOutcome) {
        this.questionOutcome = questionOutcome;
    }

    public BigDecimal getAwardedMarks() {
        return awardedMarks;
    }

    public void setAwardedMarks(BigDecimal awardedMarks) {
        this.awardedMarks = awardedMarks;
    }

    public BigDecimal getAwardedNegativeMarks() {
        return awardedNegativeMarks;
    }

    public void setAwardedNegativeMarks(BigDecimal awardedNegativeMarks) {
        this.awardedNegativeMarks = awardedNegativeMarks;
    }

    public String getRemarks() {
        return remarks;
    }

    public void setRemarks(String remarks) {
        this.remarks = remarks;
    }
}
