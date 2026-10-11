package com.company.exam.assessment.entity;

import com.company.exam.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** A schedule binding a published AssessmentVersion to a time window ({@code §5.7, §67-§72}). */
@Entity
@Table(name = "assessment_schedule")
public class AssessmentSchedule extends BaseEntity {

    @Column(name = "assessment_version_id", nullable = false)
    private UUID assessmentVersionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "mode", nullable = false, length = 20)
    private ScheduleMode mode;

    @Column(name = "start_time", nullable = false)
    private Instant startTime;

    @Column(name = "end_time", nullable = false)
    private Instant endTime;

    public UUID getAssessmentVersionId() {
        return assessmentVersionId;
    }

    public void setAssessmentVersionId(UUID assessmentVersionId) {
        this.assessmentVersionId = assessmentVersionId;
    }

    public ScheduleMode getMode() {
        return mode;
    }

    public void setMode(ScheduleMode mode) {
        this.mode = mode;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public void setStartTime(Instant startTime) {
        this.startTime = startTime;
    }

    public Instant getEndTime() {
        return endTime;
    }

    public void setEndTime(Instant endTime) {
        this.endTime = endTime;
    }
}
