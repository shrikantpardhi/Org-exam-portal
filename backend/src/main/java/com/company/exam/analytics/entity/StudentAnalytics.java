package com.company.exam.analytics.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * The current derived aggregate for one student ({@code specs/17-P8-Analytics-V1.5.md §6.1, §14-§21}).
 *
 * <p>Derived, recalculable and non-authoritative. The frozen table has no audit columns and no
 * lifecycle {@code status}, so this entity does not extend {@code BaseEntity}.
 */
@Entity
@Table(name = "student_analytics")
public class StudentAnalytics {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "assessments_attempted")
    private Integer assessmentsAttempted;

    @Column(name = "average_score", precision = 8, scale = 2)
    private BigDecimal averageScore;

    @Column(name = "accuracy", precision = 8, scale = 2)
    private BigDecimal accuracy;

    @Column(name = "average_time", precision = 10, scale = 2)
    private BigDecimal averageTime;

    @Column(name = "last_calculated_on")
    private Instant lastCalculatedOn;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public Integer getAssessmentsAttempted() {
        return assessmentsAttempted;
    }

    public void setAssessmentsAttempted(Integer assessmentsAttempted) {
        this.assessmentsAttempted = assessmentsAttempted;
    }

    public BigDecimal getAverageScore() {
        return averageScore;
    }

    public void setAverageScore(BigDecimal averageScore) {
        this.averageScore = averageScore;
    }

    public BigDecimal getAccuracy() {
        return accuracy;
    }

    public void setAccuracy(BigDecimal accuracy) {
        this.accuracy = accuracy;
    }

    public BigDecimal getAverageTime() {
        return averageTime;
    }

    public void setAverageTime(BigDecimal averageTime) {
        this.averageTime = averageTime;
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
        if (!(other instanceof StudentAnalytics that)) {
            return false;
        }
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
