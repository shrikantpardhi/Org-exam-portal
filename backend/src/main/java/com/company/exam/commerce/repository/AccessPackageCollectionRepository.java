package com.company.exam.commerce.repository;

import com.company.exam.commerce.entity.AccessPackageCollection;
import com.company.exam.common.enums.Status;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence for {@link AccessPackageCollection} ({@code specs/18-P9-Commerce-V1.5.md §7.2}). */
public interface AccessPackageCollectionRepository extends JpaRepository<AccessPackageCollection, UUID> {

    List<AccessPackageCollection> findByAccessPackageId(UUID accessPackageId);

    List<AccessPackageCollection> findByAccessPackageIdIn(Collection<UUID> accessPackageIds);

    List<AccessPackageCollection> findByAssessmentCollectionIdInAndStatus(Collection<UUID> assessmentCollectionIds,
            Status status);

    Optional<AccessPackageCollection> findByAccessPackageIdAndAssessmentCollectionId(UUID accessPackageId,
            UUID assessmentCollectionId);

    boolean existsByAccessPackageIdAndAssessmentCollectionId(UUID accessPackageId, UUID assessmentCollectionId);

    void deleteByAccessPackageIdAndAssessmentCollectionId(UUID accessPackageId, UUID assessmentCollectionId);
}
