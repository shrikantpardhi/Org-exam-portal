package com.company.exam.identity.entity;

import com.company.exam.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * User-to-role junction ({@code specs/10-P1-Identity-V1.5.md §12}).
 *
 * <p>Protected by {@code UNIQUE(user_id, role_id)}. Rows are not physically deleted to revoke
 * access when historical auditability matters; {@code status = INACTIVE} is preferred.
 */
@Entity
@Table(name = "user_role")
public class UserRole extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "role_id", nullable = false)
    private UUID roleId;

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public UUID getRoleId() {
        return roleId;
    }

    public void setRoleId(UUID roleId) {
        this.roleId = roleId;
    }
}
