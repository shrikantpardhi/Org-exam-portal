package com.company.exam.evaluation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * One versioned evaluation of a submitted attempt ({@code specs/15-P6-Evaluation-V1.5.md §5.3, §47}).
 *
 * <p>Because the frozen table's {@code status} column carries the Evaluation lifecycle vocabulary
 * (PENDING/IN_PROGRESS/COMPLETED/FAILED/SUPERSEDED) rather than the generic entity status, this
 * entity does not extend {@code BaseEntity}; it declares its own audit columns.
 *
 * <p>At most one current (non-superseded) evaluation may exist per attempt, enforced by the partial
 * unique index {@code uk_evaluation_current_attempt}.
 */
@Entity
@Table(name = "evaluation")
@EntityListeners(AuditingEntityListener.class)
public class Evaluation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "attempt_id", nullable = false)
    private UUID attemptId;

    @Column(name = "answer_key_id", nullable = false)
    private UUID answerKeyId;

    @Column(name = "version", nullable = false)
    private Integer version;

    @Column(name = "superseded_by_id")
    private UUID supersededById;

    @Column(name = "superseded_on")
    private Instant supersededOn;

    @Column(name = "superseded_reason", columnDefinition = "text")
    private String supersededReason;

    @Column(name = "evaluated_on")
    private Instant evaluatedOn;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private EvaluationStatus status = EvaluationStatus.PENDING;

    @CreatedBy
    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    @CreatedDate
    @Column(name = "created_on", updatable = false)
    private Instant createdOn;

    @LastModifiedBy
    @Column(name = "updated_by")
    private UUID updatedBy;

    @LastModifiedDate
    @Column(name = "updated_on")
    private Instant updatedOn;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getAttemptId() {
        return attemptId;
    }

    public void setAttemptId(UUID attemptId) {
        this.attemptId = attemptId;
    }

    public UUID getAnswerKeyId() {
        return answerKeyId;
    }

    public void setAnswerKeyId(UUID answerKeyId) {
        this.answerKeyId = answerKeyId;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public UUID getSupersededById() {
        return supersededById;
    }

    public void setSupersededById(UUID supersededById) {
        this.supersededById = supersededById;
    }

    public Instant getSupersededOn() {
        return supersededOn;
    }

    public void setSupersededOn(Instant supersededOn) {
        this.supersededOn = supersededOn;
    }

    public String getSupersededReason() {
        return supersededReason;
    }

    public void setSupersededReason(String supersededReason) {
        this.supersededReason = supersededReason;
    }

    public Instant getEvaluatedOn() {
        return evaluatedOn;
    }

    public void setEvaluatedOn(Instant evaluatedOn) {
        this.evaluatedOn = evaluatedOn;
    }

    public EvaluationStatus getStatus() {
        return status;
    }

    public void setStatus(EvaluationStatus status) {
        this.status = status;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(UUID createdBy) {
        this.createdBy = createdBy;
    }

    public Instant getCreatedOn() {
        return createdOn;
    }

    public void setCreatedOn(Instant createdOn) {
        this.createdOn = createdOn;
    }

    public UUID getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(UUID updatedBy) {
        this.updatedBy = updatedBy;
    }

    public Instant getUpdatedOn() {
        return updatedOn;
    }

    public void setUpdatedOn(Instant updatedOn) {
        this.updatedOn = updatedOn;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Evaluation that)) {
            return false;
        }
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
