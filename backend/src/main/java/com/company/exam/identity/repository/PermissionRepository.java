package com.company.exam.identity.repository;

import com.company.exam.identity.entity.Permission;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Persistence for {@link Permission} ({@code specs/10-P1-Identity-V1.5.md §10}). */
public interface PermissionRepository extends JpaRepository<Permission, UUID> {

    Optional<Permission> findByCode(String code);

    List<Permission> findAllByCodeIn(Collection<String> codes);

    List<Permission> findAllByIdIn(Collection<UUID> ids);

    /**
     * The effective permission codes for a user. A permission is effective only when the user,
     * role, permission, {@code user_role}, and {@code role_permission} are all ACTIVE
     * ({@code specs/10-P1-Identity-V1.5.md §11, §70}). One join query, distinct codes, no N+1.
     */
    @Query("""
            select distinct p.code
            from Permission p, RolePermission rp, Role r, UserRole ur, User u
            where rp.permissionId = p.id
              and r.id = rp.roleId
              and ur.roleId = r.id
              and u.id = ur.userId
              and u.id = :userId
              and u.status = com.company.exam.common.enums.Status.ACTIVE
              and r.status = com.company.exam.common.enums.Status.ACTIVE
              and p.status = com.company.exam.common.enums.Status.ACTIVE
              and ur.status = com.company.exam.common.enums.Status.ACTIVE
              and rp.status = com.company.exam.common.enums.Status.ACTIVE
            order by p.code
            """)
    List<String> findEffectivePermissionCodes(@Param("userId") UUID userId);

    /**
     * Effective permission catalog for a role (role-permission and permission both ACTIVE).
     */
    @Query("""
            select p
            from Permission p, RolePermission rp
            where rp.permissionId = p.id
              and rp.roleId = :roleId
              and p.status = com.company.exam.common.enums.Status.ACTIVE
              and rp.status = com.company.exam.common.enums.Status.ACTIVE
            order by p.code
            """)
    List<Permission> findEffectiveByRoleId(@Param("roleId") UUID roleId);
}
