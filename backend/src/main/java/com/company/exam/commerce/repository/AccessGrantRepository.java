package com.company.exam.commerce.repository;

import com.company.exam.commerce.entity.AccessGrant;
import com.company.exam.common.enums.Status;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Persistence for {@link AccessGrant} ({@code specs/18-P9-Commerce-V1.5.md §7.6, §14}). */
public interface AccessGrantRepository extends JpaRepository<AccessGrant, UUID>,
        JpaSpecificationExecutor<AccessGrant> {

    List<AccessGrant> findByUserId(UUID userId);

    List<AccessGrant> findByUserIdAndStatus(UUID userId, Status status);

    /** The most recent ACTIVE grant for a user and package, the merge target ({@code §19}). */
    Optional<AccessGrant> findFirstByUserIdAndAccessPackageIdAndStatusOrderByStartDateDesc(UUID userId,
            UUID accessPackageId, Status status);

    List<AccessGrant> findByUserIdAndAccessPackageIdAndStatus(UUID userId, UUID accessPackageId, Status status);

    /**
     * The time-aware authorization query ({@code §14.2}): start inclusive, end inclusive, a null
     * end date never expires, and server time is authoritative.
     */
    @Query("""
            select count(g) from AccessGrant g
             where g.userId = :userId
               and g.accessPackageId = :accessPackageId
               and g.status = com.company.exam.common.enums.Status.ACTIVE
               and g.startDate <= :now
               and (g.endDate is null or g.endDate >= :now)
            """)
    long countValid(@Param("userId") UUID userId, @Param("accessPackageId") UUID accessPackageId,
            @Param("now") Instant now);

    /** The same validity rule across a set of packages, for assessment entitlement resolution. */
    @Query("""
            select count(g) from AccessGrant g
             where g.userId = :userId
               and g.accessPackageId in :accessPackageIds
               and g.status = com.company.exam.common.enums.Status.ACTIVE
               and g.startDate <= :now
               and (g.endDate is null or g.endDate >= :now)
            """)
    long countValidForPackages(@Param("userId") UUID userId,
            @Param("accessPackageIds") Collection<UUID> accessPackageIds, @Param("now") Instant now);
}
