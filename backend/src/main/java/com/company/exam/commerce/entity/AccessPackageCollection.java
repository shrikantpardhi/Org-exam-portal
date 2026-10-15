package com.company.exam.commerce.entity;

import com.company.exam.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * The junction mapping a package to the assessment collections it unlocks
 * ({@code specs/18-P9-Commerce-V1.5.md §7.2, §9}).
 *
 * <p>The mapping is relational, never JSONB ({@code §8}).
 */
@Entity
@Table(name = "access_package_collection")
public class AccessPackageCollection extends BaseEntity {

    @Column(name = "access_package_id", nullable = false)
    private UUID accessPackageId;

    @Column(name = "assessment_collection_id", nullable = false)
    private UUID assessmentCollectionId;

    public UUID getAccessPackageId() {
        return accessPackageId;
    }

    public void setAccessPackageId(UUID accessPackageId) {
        this.accessPackageId = accessPackageId;
    }

    public UUID getAssessmentCollectionId() {
        return assessmentCollectionId;
    }

    public void setAssessmentCollectionId(UUID assessmentCollectionId) {
        this.assessmentCollectionId = assessmentCollectionId;
    }
}
