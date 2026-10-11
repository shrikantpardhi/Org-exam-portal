package com.company.exam.assessment.entity;

import com.company.exam.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

/** A collection-to-assessment association ({@code §5.9, §77}). */
@Entity
@Table(name = "collection_assessment")
public class CollectionAssessment extends BaseEntity {

    @Column(name = "collection_id", nullable = false)
    private UUID collectionId;

    @Column(name = "assessment_id", nullable = false)
    private UUID assessmentId;

    public UUID getCollectionId() {
        return collectionId;
    }

    public void setCollectionId(UUID collectionId) {
        this.collectionId = collectionId;
    }

    public UUID getAssessmentId() {
        return assessmentId;
    }

    public void setAssessmentId(UUID assessmentId) {
        this.assessmentId = assessmentId;
    }
}
