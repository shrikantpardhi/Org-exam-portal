package com.company.exam.delivery.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Objects;
import java.util.UUID;

/**
 * The immutable option-ordering snapshot for one attempt question
 * ({@code specs/14-P5-Delivery-V1.5.md §10, §25}).
 *
 * <p>It preserves the exact option ordering presented to the student and is immutable after
 * {@code snapshot_status = READY}. The frozen table has no audit columns, so it does not extend
 * {@code BaseEntity}.
 */
@Entity
@Table(name = "attempt_question_option")
public class AttemptQuestionOption {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "attempt_question_id", nullable = false)
    private UUID attemptQuestionId;

    @Column(name = "question_option_id", nullable = false)
    private UUID questionOptionId;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getAttemptQuestionId() {
        return attemptQuestionId;
    }

    public void setAttemptQuestionId(UUID attemptQuestionId) {
        this.attemptQuestionId = attemptQuestionId;
    }

    public UUID getQuestionOptionId() {
        return questionOptionId;
    }

    public void setQuestionOptionId(UUID questionOptionId) {
        this.questionOptionId = questionOptionId;
    }

    public Integer getDisplayOrder() {
        return displayOrder;
    }

    public void setDisplayOrder(Integer displayOrder) {
        this.displayOrder = displayOrder;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof AttemptQuestionOption that)) {
            return false;
        }
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
