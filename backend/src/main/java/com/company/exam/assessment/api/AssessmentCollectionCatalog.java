package com.company.exam.assessment.api;

import java.util.List;
import java.util.UUID;

/**
 * The Assessment application contract consumed by Commerce to resolve package entitlement
 * ({@code specs/18-P9-Commerce-V1.5.md §9.1, §100}).
 *
 * <p>Commerce resolves assessment entitlement through this contract rather than traversing
 * Assessment repositories directly.
 */
public interface AssessmentCollectionCatalog {

    /** The assessment collections that contain the assessment. */
    List<UUID> collectionIdsForAssessment(UUID assessmentId);

    /** Whether the collection exists and is ACTIVE. */
    boolean collectionExists(UUID assessmentCollectionId);
}
