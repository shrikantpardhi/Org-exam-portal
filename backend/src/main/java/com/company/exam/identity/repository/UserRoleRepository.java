package com.company.exam.identity.repository;

import com.company.exam.common.enums.Status;
import com.company.exam.identity.entity.UserRole;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Persistence for {@link UserRole} ({@code specs/10-P1-Identity-V1.5.md §12}). */
public interface UserRoleRepository extends JpaRepository<UserRole, UUID> {

    List<UserRole> findByUserId(UUID userId);

    List<UserRole> findByUserIdAndStatus(UUID userId, Status status);

    List<UserRole> findByUserIdInAndStatus(Collection<UUID> userIds, Status status);

    Optional<UserRole> findByUserIdAndRoleId(UUID userId, UUID roleId);

    List<UserRole> findByRoleIdAndStatus(UUID roleId, Status status);

    /**
     * Counts active assignments for a role code. Used by the last-admin rule
     * ({@code specs/10-P1-Identity-V1.5.md §63}).
     */
    @Query("""
            select count(ur)
            from UserRole ur, Role r
            where r.id = ur.roleId
              and r.code = :roleCode
              and ur.status = com.company.exam.common.enums.Status.ACTIVE
            """)
    long countActiveAssignmentsByRoleCode(@Param("roleCode") String roleCode);

    /**
     * Counts active assignments for a role code within one organization. Backs the one-admin-per-org
     * cap (ADR-0032, {@code MAX_ADMINS_PER_ORG}) and the platform org detail admin count.
     */
    @Query("""
            select count(ur)
            from UserRole ur, Role r, User u
            where r.id = ur.roleId
              and u.id = ur.userId
              and r.code = :roleCode
              and u.organizationId = :organizationId
              and ur.status = com.company.exam.common.enums.Status.ACTIVE
            """)
    long countActiveAssignmentsByRoleCodeInOrganization(@Param("roleCode") String roleCode,
            @Param("organizationId") UUID organizationId);
}
