package com.company.exam.identity.entity;

import com.company.exam.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * A named set of permissions ({@code specs/10-P1-Identity-V1.5.md §9}).
 *
 * <p>Role codes are unique per scope (ADR-0045, D-05): a null {@code organizationId} means the role is
 * platform-wide and shared by every organization (the seven seeded roles, referenced by code from
 * {@link com.company.exam.identity.IdentityConstants}), while a non-null value means the role belongs
 * to that organization. Roles are not a security boundary by name: authorization decisions are made
 * on permissions, not on role labels.
 */
@Entity
@Table(name = "role")
public class Role extends BaseEntity {

    @Column(name = "code", nullable = false, length = 50)
    private String code;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    /**
     * Owning organization, or {@code null} for a platform-wide role (ADR-0045).
     *
     * <p>Kept as a plain id rather than an association: the identity module resolves organizations by
     * id and a lazy association here would add a fetch to every role read on the authorization path.
     */
    @Column(name = "organization_id")
    private java.util.UUID organizationId;

    public java.util.UUID getOrganizationId() {
        return organizationId;
    }

    public void setOrganizationId(java.util.UUID organizationId) {
        this.organizationId = organizationId;
    }

    /** True when this role is shared by every organization (ADR-0045). */
    public boolean isPlatformWide() {
        return organizationId == null;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
