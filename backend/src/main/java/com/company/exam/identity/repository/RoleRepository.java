package com.company.exam.identity.repository;

import com.company.exam.identity.entity.Role;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Persistence for {@link Role} ({@code specs/10-P1-Identity-V1.5.md §9}). */
public interface RoleRepository extends JpaRepository<Role, UUID> {

    /**
     * A platform-wide role by code (ADR-0045).
     *
     * <p>This is the correct lookup for every role resolved from {@code IdentityConstants}: those are
     * the seeded roles, and after V35 a business organization may own a role with the same code.
     * Resolving {@code "ADMIN"} with an unscoped lookup would eventually return the wrong row, so the
     * unscoped variant is deliberately not declared.
     */
    Optional<Role> findByCodeAndOrganizationIdIsNull(String code);

    /** A role owned by one organization (ADR-0045). */
    Optional<Role> findByCodeAndOrganizationId(String code, UUID organizationId);

    /**
     * Every role visible to an organization: its own roles plus the platform-wide ones (ADR-0045).
     *
     * <p>This is the read scope for the role catalogue. A platform-wide role is visible everywhere
     * because it is shared; an organization-owned role is visible only to its owner.
     */
    List<Role> findByOrganizationIdIsNullOrOrganizationId(UUID organizationId, UUID sameOrganizationId);

    boolean existsByCodeAndOrganizationIdIsNull(String code);

    boolean existsByCodeAndOrganizationId(String code, UUID organizationId);

    List<Role> findAllByCodeIn(Collection<String> codes);

    List<Role> findAllByIdIn(Collection<UUID> ids);

    /**
     * Effective role codes for a user: the role, the assignment, and the user must all be ACTIVE
     * ({@code specs/10-P1-Identity-V1.5.md §12, §70}).
     */
    @Query("""
            select distinct r.code
            from Role r, UserRole ur, User u
            where ur.roleId = r.id
              and u.id = ur.userId
              and u.id = :userId
              and u.status = com.company.exam.common.enums.Status.ACTIVE
              and r.status = com.company.exam.common.enums.Status.ACTIVE
              and ur.status = com.company.exam.common.enums.Status.ACTIVE
            order by r.code
            """)
    List<String> findEffectiveRoleCodes(@Param("userId") UUID userId);
}
