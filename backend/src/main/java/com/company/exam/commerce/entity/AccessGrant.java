package com.company.exam.commerce.entity;

import com.company.exam.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * The authoritative authorization record for paid access
 * ({@code specs/18-P9-Commerce-V1.5.md §7.6, §14}).
 *
 * <p>{@code status} uses the standard lifecycle (ACTIVE/INACTIVE/ARCHIVED). No grant source/type
 * column exists and none may be added without amendment ({@code §64}).
 */
@Entity
@Table(name = "access_grant")
public class AccessGrant extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "access_package_id", nullable = false)
    private UUID accessPackageId;

    @Column(name = "start_date", nullable = false)
    private Instant startDate;

    @Column(name = "end_date")
    private Instant endDate;

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public UUID getAccessPackageId() {
        return accessPackageId;
    }

    public void setAccessPackageId(UUID accessPackageId) {
        this.accessPackageId = accessPackageId;
    }

    public Instant getStartDate() {
        return startDate;
    }

    public void setStartDate(Instant startDate) {
        this.startDate = startDate;
    }

    public Instant getEndDate() {
        return endDate;
    }

    public void setEndDate(Instant endDate) {
        this.endDate = endDate;
    }
}
