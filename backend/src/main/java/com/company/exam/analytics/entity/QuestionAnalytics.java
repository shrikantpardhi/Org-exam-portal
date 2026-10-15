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
 * The current derived aggregate for one parent Question ({@code specs/17-P8-Analytics-V1.5.md §6.3, §28-§35}).
 *
 * <p>The frozen schema stores parent-Question grain only ({@code §30, §76}). No audit columns, so it
 * does not extend {@code BaseEntity}.
 */
@Entity
@Table(name = "question_analytics")
public class QuestionAnalytics {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "question_id", nullable = false, updatable = false)
    private UUID questionId;

    @Column(name = "total_attempts")
    private Integer totalAttempts;

    @Column(name = "correct_attempts")
    private Integer correctAttempts;

    @Column(name = "incorrect_attempts")
    private Integer incorrectAttempts;

    @Column(name = "average_time", precision = 10, scale = 2)
    private BigDecimal averageTime;

    @Column(name = "difficulty_index", precision = 8, scale = 4)
    private BigDecimal difficultyIndex;

    @Column(name = "last_calculated_on")
    private Instant lastCalculatedOn;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getQuestionId() {
        return questionId;
    }

    public void setQuestionId(UUID questionId) {
        this.questionId = questionId;
    }

    public Integer getTotalAttempts() {
        return totalAttempts;
    }

    public void setTotalAttempts(Integer totalAttempts) {
        this.totalAttempts = totalAttempts;
    }

    public Integer getCorrectAttempts() {
        return correctAttempts;
    }

    public void setCorrectAttempts(Integer correctAttempts) {
        this.correctAttempts = correctAttempts;
    }

    public Integer getIncorrectAttempts() {
        return incorrectAttempts;
    }

    public void setIncorrectAttempts(Integer incorrectAttempts) {
        this.incorrectAttempts = incorrectAttempts;
    }

    public BigDecimal getAverageTime() {
        return averageTime;
    }

    public void setAverageTime(BigDecimal averageTime) {
        this.averageTime = averageTime;
    }

    public BigDecimal getDifficultyIndex() {
        return difficultyIndex;
    }

    public void setDifficultyIndex(BigDecimal difficultyIndex) {
        this.difficultyIndex = difficultyIndex;
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
        if (!(other instanceof QuestionAnalytics that)) {
            return false;
        }
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
