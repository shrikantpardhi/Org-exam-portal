package com.company.exam.identity.repository;

import com.company.exam.common.enums.Status;
import com.company.exam.identity.entity.RolePermission;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence for {@link RolePermission} ({@code specs/10-P1-Identity-V1.5.md §13}). */
public interface RolePermissionRepository extends JpaRepository<RolePermission, UUID> {

    List<RolePermission> findByRoleId(UUID roleId);

    List<RolePermission> findByRoleIdAndStatus(UUID roleId, Status status);
}
