package com.company.exam.administration.entity;

import com.company.exam.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * The authoritative storage for approved organization-level configuration
 * ({@code specs/20-P11-Administration-V1.5.md §8}).
 *
 * <p>It is not a secret store, a transaction store, or a user-data repository ({@code §8.2}). Its
 * JSONB document is validated against the recognized-key registry ({@code §8.3, §8.6}).
 */
@Entity
@Table(name = "organization_setting")
public class OrganizationSetting extends BaseEntity {

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "configuration", nullable = false, columnDefinition = "jsonb")
    private String configuration;

    public UUID getOrganizationId() {
        return organizationId;
    }

    public void setOrganizationId(UUID organizationId) {
        this.organizationId = organizationId;
    }

    public String getConfiguration() {
        return configuration;
    }

    public void setConfiguration(String configuration) {
        this.configuration = configuration;
    }
}
