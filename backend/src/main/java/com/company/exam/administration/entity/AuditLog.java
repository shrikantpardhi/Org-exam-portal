package com.company.exam.administration.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

/**
 * The immutable audit trail ({@code specs/20-P11-Administration-V1.5.md §11}).
 *
 * <p>Insert-only: updates and deletions are prohibited and no update/delete endpoint is exposed
 * ({@code §11.2}). The frozen schema carries no request id, old/new values, IP address or payload
 * field, so AuditLog is not a payload archive ({@code §11.3, §11.7, §11.9}).
 *
 * <p>The entity is deliberately not a {@code BaseEntity}: an audit row has no status and no audit
 * columns of its own. Audit insertion participates in the business transaction, so a failed audit
 * rolls the business mutation back ({@code §11.11}).
 */
@Entity
@Immutable
@Table(name = "audit_log")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", updatable = false)
    private UUID userId;

    @Column(name = "entity_name", length = 150, updatable = false)
    private String entityName;

    @Column(name = "entity_id", updatable = false)
    private UUID entityId;

    @Column(name = "action", length = 100, updatable = false)
    private String action;

    @Column(name = "remarks", columnDefinition = "text", updatable = false)
    private String remarks;

    /**
     * The organization this row belongs to, or {@code null} for platform-wide activity (ADR-0046,
     * D-11). Recorded at write time rather than derived at read time, because derivation silently
     * reports nothing for entities that have no user (organizations, business rules, providers).
     */
    @Column(name = "organization_id", updatable = false)
    private UUID organizationId;

    @Column(name = "created_on", updatable = false)
    private Instant createdOn;

    public UUID getOrganizationId() {
        return organizationId;
    }

    public void setOrganizationId(UUID organizationId) {
        this.organizationId = organizationId;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public String getEntityName() {
        return entityName;
    }

    public void setEntityName(String entityName) {
        this.entityName = entityName;
    }

    public UUID getEntityId() {
        return entityId;
    }

    public void setEntityId(UUID entityId) {
        this.entityId = entityId;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getRemarks() {
        return remarks;
    }

    public void setRemarks(String remarks) {
        this.remarks = remarks;
    }

    public Instant getCreatedOn() {
        return createdOn;
    }

    public void setCreatedOn(Instant createdOn) {
        this.createdOn = createdOn;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof AuditLog that)) {
            return false;
        }
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
