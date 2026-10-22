package com.company.exam.evaluation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * One correct option of a multi-correct {@code answer_key_detail} (ADR-0025, GAP-072).
 *
 * <p>When an {@link AnswerKeyDetail} has one or more rows here, the set of referenced options is
 * the authoritative correct option set for evaluation. When none exist, {@code correct_option_id}
 * (single) or {@code correct_answer_text} (free text) applies.
 */
@Entity
@Table(name = "answer_key_detail_option")
public class AnswerKeyDetailOption {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "answer_key_detail_id", nullable = false)
    private UUID answerKeyDetailId;

    @Column(name = "question_option_id", nullable = false)
    private UUID questionOptionId;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getAnswerKeyDetailId() {
        return answerKeyDetailId;
    }

    public void setAnswerKeyDetailId(UUID answerKeyDetailId) {
        this.answerKeyDetailId = answerKeyDetailId;
    }

    public UUID getQuestionOptionId() {
        return questionOptionId;
    }

    public void setQuestionOptionId(UUID questionOptionId) {
        this.questionOptionId = questionOptionId;
    }
}