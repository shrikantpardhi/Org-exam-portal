package com.company.exam.delivery.entity;

import com.company.exam.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * A persisted student answer for one attempt question ({@code specs/14-P5-Delivery-V1.5.md §11}).
 *
 * <p>It carries the lifecycle {@code status} plus audit columns, so it extends {@code BaseEntity}.
 * Stale-write protection uses HTTP conditional requests at the API boundary; the ETag is derived
 * from {@code updated_on}. No {@code response_version} column is added ({@code §155}).
 *
 * <p>{@code UNIQUE(attempt_id, question_version_id)} means a save for an existing pair is an
 * update, never a second row.
 */
@Entity
@Table(name = "student_response")
public class StudentResponse extends BaseEntity {

    @Column(name = "attempt_id", nullable = false)
    private UUID attemptId;

    @Column(name = "question_version_id", nullable = false)
    private UUID questionVersionId;

    @Column(name = "response", columnDefinition = "text")
    private String response;

    @Column(name = "time_spent_seconds")
    private Integer timeSpentSeconds;

    @Column(name = "marked_for_review")
    private Boolean markedForReview;

    @Column(name = "visited")
    private Boolean visited;

    public UUID getAttemptId() {
        return attemptId;
    }

    public void setAttemptId(UUID attemptId) {
        this.attemptId = attemptId;
    }

    public UUID getQuestionVersionId() {
        return questionVersionId;
    }

    public void setQuestionVersionId(UUID questionVersionId) {
        this.questionVersionId = questionVersionId;
    }

    public String getResponse() {
        return response;
    }

    public void setResponse(String response) {
        this.response = response;
    }

    public Integer getTimeSpentSeconds() {
        return timeSpentSeconds;
    }

    public void setTimeSpentSeconds(Integer timeSpentSeconds) {
        this.timeSpentSeconds = timeSpentSeconds;
    }

    public Boolean getMarkedForReview() {
        return markedForReview;
    }

    public void setMarkedForReview(Boolean markedForReview) {
        this.markedForReview = markedForReview;
    }

    public Boolean getVisited() {
        return visited;
    }

    public void setVisited(Boolean visited) {
        this.visited = visited;
    }
}
