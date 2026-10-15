package com.company.exam.evaluation.entity;

import com.company.exam.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * A versioned answer key bound to exactly one AssessmentVersion
 * ({@code specs/15-P6-Evaluation-V1.5.md §5.1, §25-§28}).
 *
 * <p>A published key ({@code published = true}) is immutable; corrections are represented by a new
 * version, never by editing a published one ({@code §27}).
 */
@Entity
@Table(name = "answer_key")
public class AnswerKey extends BaseEntity {

    @Column(name = "assessment_version_id", nullable = false)
    private UUID assessmentVersionId;

    @Column(name = "version", nullable = false)
    private Integer version;

    @Column(name = "is_published", nullable = false)
    private boolean published;

    @Column(name = "published_by")
    private UUID publishedBy;

    @Column(name = "published_on")
    private Instant publishedOn;

    public UUID getAssessmentVersionId() {
        return assessmentVersionId;
    }

    public void setAssessmentVersionId(UUID assessmentVersionId) {
        this.assessmentVersionId = assessmentVersionId;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
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
