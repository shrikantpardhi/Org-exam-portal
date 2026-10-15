package com.company.exam.analytics.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * The current derived aggregate for one organization ({@code specs/17-P8-Analytics-V1.5.md §6.5, §40-§44}).
 *
 * <p>Revenue analytics remains Commerce-owned and is not part of P8 ({@code §40}).
 */
@Entity
@Table(name = "organization_analytics")
public class OrganizationAnalytics {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "total_students")
    private Integer totalStudents;

    @Column(name = "total_assessments")
    private Integer totalAssessments;

    @Column(name = "total_attempts")
    private Integer totalAttempts;

    @Column(name = "active_users")
    private Integer activeUsers;

    @Column(name = "last_calculated_on")
    private Instant lastCalculatedOn;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public void setOrganizationId(UUID organizationId) {
        this.organizationId = organizationId;
    }

    public Integer getTotalStudents() {
        return totalStudents;
    }

    public void setTotalStudents(Integer totalStudents) {
        this.totalStudents = totalStudents;
    }

    public Integer getTotalAssessments() {
        return totalAssessments;
    }

    public void setTotalAssessments(Integer totalAssessments) {
        this.totalAssessments = totalAssessments;
    }

    public Integer getTotalAttempts() {
        return totalAttempts;
    }

    public void setTotalAttempts(Integer totalAttempts) {
        this.totalAttempts = totalAttempts;
    }

    public Integer getActiveUsers() {
        return activeUsers;
    }

    public void setActiveUsers(Integer activeUsers) {
        this.activeUsers = activeUsers;
    }

    public Instant getLastCalculatedOn() {
        return lastCalculatedOn;
    }

    public void setLastCalculatedOn(Instant lastCalculatedOn) {
        this.lastCalculatedOn = lastCalculatedOn;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof OrganizationAnalytics that)) {
            return false;
        }
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
