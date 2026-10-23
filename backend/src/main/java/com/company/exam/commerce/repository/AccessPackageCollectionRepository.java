package com.company.exam.commerce.repository;

import com.company.exam.commerce.entity.AccessPackageCollection;
import com.company.exam.common.enums.Status;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    /**
     * The paid-content decision for a set of collections in one statement (GAP-059).
     *
     * <p>Returns one row per ACTIVE package that grants access to any of these collections, with
     * {@code true} in the second column when the user holds a currently valid grant for it. An empty
     * result means no package grants the assessment, i.e. free content (ADR-0026); a non-empty
     * result with no {@code true} is paid content the user is not entitled to.
     *
     * <p>This replaces the previous two reads (the package mappings, then a validity count over
     * them) with a single statement, preserving the exact decision semantics
     * ({@code specs/18-P9-Commerce-V1.5.md §3.2, §14, §28.1}).
     */
    @Query("""
            select distinct apc.accessPackageId,
                   case when g.id is null then false else true end
              from AccessPackageCollection apc
              left join AccessGrant g
                     on g.accessPackageId = apc.accessPackageId
                    and g.userId = :userId
                    and g.status = com.company.exam.common.enums.Status.ACTIVE
                    and g.startDate <= :now
                    and (g.endDate is null or g.endDate >= :now)
             where apc.assessmentCollectionId in :assessmentCollectionIds
               and apc.status = com.company.exam.common.enums.Status.ACTIVE
            """)
    List<Object[]> findPackageGrants(@Param("userId") UUID userId,
            @Param("assessmentCollectionIds") Collection<UUID> assessmentCollectionIds,
            @Param("now") Instant now);
}
