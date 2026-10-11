package com.company.exam.identity.entity;

import com.company.exam.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * Role-to-permission junction ({@code specs/10-P1-Identity-V1.5.md §13}).
 *
 * <p>Protected by {@code UNIQUE(role_id, permission_id)}. A permission is effective only when the
 * role, permission, and this relationship are all {@code ACTIVE}.
 */
@Entity
@Table(name = "role_permission")
public class RolePermission extends BaseEntity {

    @Column(name = "role_id", nullable = false)
    private UUID roleId;

    @Column(name = "permission_id", nullable = false)
    private UUID permissionId;

    public UUID getRoleId() {
        return roleId;
    }

    public void setRoleId(UUID roleId) {
        this.roleId = roleId;
    }

    public UUID getPermissionId() {
        return permissionId;
    }

    public void setPermissionId(UUID permissionId) {
        this.permissionId = permissionId;
    }
}
