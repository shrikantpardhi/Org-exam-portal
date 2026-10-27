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
     * Counts active assignments for a role code within one organization. Backs the admin cap
     * (ADR-0032/ADR-0054, {@code MAX_ADMINS_PER_ORG}) and the platform org detail admin count.
     *
     * <p><b>{@code u.status <> ARCHIVED} is deliberate (ADR-0054).</b> Archiving an administrator keeps
     * their {@code user_role} row ACTIVE so un-archive can restore the admin without replaying history;
     * without this exclusion an archived admin would keep occupying a cap slot forever, and a
     * replacement could never be created. An archived account is not a competitor for a slot in any
     * sense an operator means. Any non-archived status still counts, so an INACTIVE user holding the
     * role is still counted — that is a state someone has to resolve, not a free slot.
     */
    @Query("""
            select count(ur)
            from UserRole ur, Role r, User u
            where r.id = ur.roleId
              and u.id = ur.userId
              and r.code = :roleCode
              and u.organizationId = :organizationId
              and ur.status = com.company.exam.common.enums.Status.ACTIVE
              and u.status <> com.company.exam.common.enums.Status.ARCHIVED
            """)
    long countActiveAssignmentsByRoleCodeInOrganization(@Param("roleCode") String roleCode,
            @Param("organizationId") UUID organizationId);

    /**
     * The user ids holding an active assignment for a role code within one organization.
     *
     * <p>Backs the platform administrator list (ADR-0053). Returning ids rather than entities keeps
     * the role join out of the pageable query, so paging and the total count are computed over users
     * and cannot be inflated by duplicate assignment rows.
     *
     * <p>Includes archived accounts on purpose (ADR-0054): the platform screen must be able to show an
     * archived administrator so it can be un-archived. The caller filters by account status when it
     * wants only current administrators.
     */
    @Query("""
            select ur.userId
            from UserRole ur, Role r, User u
            where r.id = ur.roleId
              and u.id = ur.userId
              and r.code = :roleCode
              and u.organizationId = :organizationId
              and ur.status = com.company.exam.common.enums.Status.ACTIVE
            """)
    List<UUID> findActiveUserIdsByRoleCodeInOrganization(@Param("roleCode") String roleCode,
            @Param("organizationId") UUID organizationId);
}
