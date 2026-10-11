package com.company.exam.delivery.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * The runtime examination session ({@code specs/14-P5-Delivery-V1.5.md §7, §13}).
 *
 * <p>The frozen table has no generic {@code status} column and no audit columns; it tracks its own
 * lifecycle via {@code session_status} and the activity timestamps, so it does not extend
 * {@code BaseEntity}. No column is added beyond the frozen schema.
 */
@Entity
@Table(name = "assessment_session")
public class AssessmentSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "assessment_schedule_id", nullable = false)
    private UUID assessmentScheduleId;

    @Column(name = "attempt_id", nullable = false)
    private UUID attemptId;

    @Enumerated(EnumType.STRING)
    @Column(name = "session_status", nullable = false, length = 20)
    private AssessmentSessionStatus sessionStatus = AssessmentSessionStatus.ACTIVE;

    @Column(name = "started_on")
    private Instant startedOn;

    @Column(name = "last_activity_on")
    private Instant lastActivityOn;

    @Column(name = "ended_on")
    private Instant endedOn;

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

    public UUID getAssessmentScheduleId() {
        return assessmentScheduleId;
    }

    public void setAssessmentScheduleId(UUID assessmentScheduleId) {
        this.assessmentScheduleId = assessmentScheduleId;
    }

    public UUID getAttemptId() {
        return attemptId;
    }

    public void setAttemptId(UUID attemptId) {
        this.attemptId = attemptId;
    }

    public AssessmentSessionStatus getSessionStatus() {
        return sessionStatus;
    }

    public void setSessionStatus(AssessmentSessionStatus sessionStatus) {
        this.sessionStatus = sessionStatus;
    }

    public Instant getStartedOn() {
        return startedOn;
    }

    public void setStartedOn(Instant startedOn) {
        this.startedOn = startedOn;
    }

    public Instant getLastActivityOn() {
        return lastActivityOn;
    }

    public void setLastActivityOn(Instant lastActivityOn) {
        this.lastActivityOn = lastActivityOn;
    }

    public Instant getEndedOn() {
        return endedOn;
    }

    public void setEndedOn(Instant endedOn) {
        this.endedOn = endedOn;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof AssessmentSession that)) {
            return false;
        }
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
