package com.company.exam.assessment.entity;

import com.company.exam.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A versioned assessment bound to an exact BlueprintVersion
 * ({@code specs/13-P4-Assessment-V1.5.md §5.4, §23, §50}).
 *
 * <p>Engine type and delivery policy are required for publication and immutable after it.
 */
@Entity
@Table(name = "assessment_version")
public class AssessmentVersion extends BaseEntity {

    @Column(name = "assessment_id", nullable = false)
    private UUID assessmentId;

    @Column(name = "blueprint_version_id", nullable = false)
    private UUID blueprintVersionId;

    @Column(name = "version", nullable = false)
    private Integer version;

    @Column(name = "total_questions")
    private Integer totalQuestions;

    @Column(name = "total_marks", precision = 8, scale = 2)
    private BigDecimal totalMarks;

    @Column(name = "duration_minutes")
    private Integer durationMinutes;

    @Enumerated(EnumType.STRING)
    @Column(name = "engine_type", length = 20)
    private EngineType engineType;

    @Column(name = "engine_configuration_version")
    private Integer engineConfigurationVersion;

    @Column(name = "delivery_policy_id")
    private UUID deliveryPolicyId;

    @Column(name = "is_published", nullable = false)
    private boolean published;

    @Column(name = "published_by")
    private UUID publishedBy;

    @Column(name = "published_on")
    private Instant publishedOn;

    public UUID getAssessmentId() {
        return assessmentId;
    }

    public void setAssessmentId(UUID assessmentId) {
        this.assessmentId = assessmentId;
    }

    public UUID getBlueprintVersionId() {
        return blueprintVersionId;
    }

    public void setBlueprintVersionId(UUID blueprintVersionId) {
        this.blueprintVersionId = blueprintVersionId;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public Integer getTotalQuestions() {
        return totalQuestions;
    }

    public void setTotalQuestions(Integer totalQuestions) {
        this.totalQuestions = totalQuestions;
    }

    public BigDecimal getTotalMarks() {
        return totalMarks;
    }

    public void setTotalMarks(BigDecimal totalMarks) {
        this.totalMarks = totalMarks;
    }

    public Integer getDurationMinutes() {
        return durationMinutes;
    }

    public void setDurationMinutes(Integer durationMinutes) {
        this.durationMinutes = durationMinutes;
    }

    public EngineType getEngineType() {
        return engineType;
    }

    public void setEngineType(EngineType engineType) {
        this.engineType = engineType;
    }

    public Integer getEngineConfigurationVersion() {
        return engineConfigurationVersion;
    }

    public void setEngineConfigurationVersion(Integer engineConfigurationVersion) {
        this.engineConfigurationVersion = engineConfigurationVersion;
    }

    public UUID getDeliveryPolicyId() {
        return deliveryPolicyId;
    }

    public void setDeliveryPolicyId(UUID deliveryPolicyId) {
        this.deliveryPolicyId = deliveryPolicyId;
    }

    public boolean isPublished() {
        return published;
    }

    public void setPublished(boolean published) {
        this.published = published;
    }

    public UUID getPublishedBy() {
        return publishedBy;
    }

    public void setPublishedBy(UUID publishedBy) {
        this.publishedBy = publishedBy;
    }

    public Instant getPublishedOn() {
        return publishedOn;
    }

    public void setPublishedOn(Instant publishedOn) {
        this.publishedOn = publishedOn;
    }
}
