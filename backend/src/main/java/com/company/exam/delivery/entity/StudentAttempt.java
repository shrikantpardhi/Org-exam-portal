package com.company.exam.delivery.entity;

import com.company.exam.assessment.entity.ScheduleMode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * The academic runtime record for one candidate against one exact published AssessmentVersion
 * ({@code specs/14-P5-Delivery-V1.5.md §8, §30}).
 *
 * <p>It has no generic {@code status} column: it carries {@code attempt_status} and the separate
 * {@code snapshot_status} state machine, so it does not extend {@code BaseEntity}.
 *
 * <p>{@code attempt_mode} is immutable after preparation ({@code §14 invariant 4}).
 */
@Entity
@Table(name = "student_attempt")
@EntityListeners(AuditingEntityListener.class)
public class StudentAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "assessment_version_id", nullable = false)
    private UUID assessmentVersionId;

    @Column(name = "assessment_schedule_id", nullable = false)
    private UUID assessmentScheduleId;

    @Enumerated(EnumType.STRING)
    @Column(name = "attempt_mode", nullable = false, length = 20)
    private ScheduleMode attemptMode;

    @Enumerated(EnumType.STRING)
    @Column(name = "attempt_status", nullable = false, length = 20)
    private AttemptStatus attemptStatus = AttemptStatus.NOT_STARTED;

    @Enumerated(EnumType.STRING)
    @Column(name = "snapshot_status", nullable = false, length = 20)
    private SnapshotStatus snapshotStatus = SnapshotStatus.PENDING;

    @Column(name = "snapshot_prepared_on")
    private Instant snapshotPreparedOn;

    @Column(name = "started_on")
    private Instant startedOn;

    @Column(name = "submitted_on")
    private Instant submittedOn;

    @Column(name = "total_score", precision = 8, scale = 2)
    private BigDecimal totalScore;

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

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public UUID getAssessmentVersionId() {
        return assessmentVersionId;
    }

    public void setAssessmentVersionId(UUID assessmentVersionId) {
        this.assessmentVersionId = assessmentVersionId;
    }

    public UUID getAssessmentScheduleId() {
        return assessmentScheduleId;
    }

    public void setAssessmentScheduleId(UUID assessmentScheduleId) {
        this.assessmentScheduleId = assessmentScheduleId;
    }

    public ScheduleMode getAttemptMode() {
        return attemptMode;
    }

    public void setAttemptMode(ScheduleMode attemptMode) {
        this.attemptMode = attemptMode;
    }

    public AttemptStatus getAttemptStatus() {
        return attemptStatus;
    }

    public void setAttemptStatus(AttemptStatus attemptStatus) {
        this.attemptStatus = attemptStatus;
    }

    public SnapshotStatus getSnapshotStatus() {
        return snapshotStatus;
    }

    public void setSnapshotStatus(SnapshotStatus snapshotStatus) {
        this.snapshotStatus = snapshotStatus;
    }

    public Instant getSnapshotPreparedOn() {
        return snapshotPreparedOn;
    }

    public void setSnapshotPreparedOn(Instant snapshotPreparedOn) {
        this.snapshotPreparedOn = snapshotPreparedOn;
    }

    public Instant getStartedOn() {
        return startedOn;
    }

    public void setStartedOn(Instant startedOn) {
        this.startedOn = startedOn;
    }

    public Instant getSubmittedOn() {
        return submittedOn;
    }

    public void setSubmittedOn(Instant submittedOn) {
        this.submittedOn = submittedOn;
    }

    public BigDecimal getTotalScore() {
        return totalScore;
    }

    public void setTotalScore(BigDecimal totalScore) {
        this.totalScore = totalScore;
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
        if (!(other instanceof StudentAttempt that)) {
            return false;
        }
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
