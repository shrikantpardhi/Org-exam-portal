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
 * The current derived aggregate for one assessment ({@code specs/17-P8-Analytics-V1.5.md §6.2, §22-§27}).
 *
 * <p>Grain is the Assessment, not the AssessmentVersion ({@code §42, §77}). No audit columns, so it
 * does not extend {@code BaseEntity}.
 */
@Entity
@Table(name = "assessment_analytics")
public class AssessmentAnalytics {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "assessment_id", nullable = false, updatable = false)
    private UUID assessmentId;

    @Column(name = "total_attempts")
    private Integer totalAttempts;

    @Column(name = "average_score", precision = 8, scale = 2)
    private BigDecimal averageScore;

    @Column(name = "highest_score", precision = 8, scale = 2)
    private BigDecimal highestScore;

    @Column(name = "lowest_score", precision = 8, scale = 2)
    private BigDecimal lowestScore;

    @Column(name = "last_calculated_on")
    private Instant lastCalculatedOn;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getAssessmentId() {
        return assessmentId;
    }

    public void setAssessmentId(UUID assessmentId) {
        this.assessmentId = assessmentId;
    }

    public Integer getTotalAttempts() {
        return totalAttempts;
    }

    public void setTotalAttempts(Integer totalAttempts) {
        this.totalAttempts = totalAttempts;
    }

    public BigDecimal getAverageScore() {
        return averageScore;
    }

    public void setAverageScore(BigDecimal averageScore) {
        this.averageScore = averageScore;
    }

    public BigDecimal getHighestScore() {
        return highestScore;
    }

    public void setHighestScore(BigDecimal highestScore) {
        this.highestScore = highestScore;
    }

    public BigDecimal getLowestScore() {
        return lowestScore;
    }

    public void setLowestScore(BigDecimal lowestScore) {
        this.lowestScore = lowestScore;
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
        if (!(other instanceof AssessmentAnalytics that)) {
            return false;
        }
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
