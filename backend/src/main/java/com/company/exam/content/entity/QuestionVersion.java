package com.company.exam.content.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * The exact content of a question at a version ({@code specs/12-P3-Content-V1.5.md §10.3}).
 *
 * <p>The frozen table has no {@code status} and no audit columns; it uses {@code is_published} and
 * {@code review_status}. It therefore does not extend {@code BaseEntity}. No column is added beyond
 * the frozen schema.
 */
@Entity
@Table(name = "question_version")
public class QuestionVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "question_id", nullable = false)
    private UUID questionId;

    @Column(name = "version", nullable = false)
    private Integer version;

    @Column(name = "title", length = 255)
    private String title;

    @Column(name = "content", columnDefinition = "text")
    private String content;

    @Column(name = "marks", precision = 8, scale = 2)
    private BigDecimal marks;

    @Column(name = "negative_marks", precision = 8, scale = 2)
    private BigDecimal negativeMarks;

    @Enumerated(EnumType.STRING)
    @Column(name = "difficulty", length = 20)
    private Difficulty difficulty;

    @Column(name = "language_id", nullable = false)
    private UUID languageId;

    @Column(name = "is_published", nullable = false)
    private boolean published;

    @Column(name = "published_by")
    private UUID publishedBy;

    @Column(name = "published_on")
    private Instant publishedOn;

    @Enumerated(EnumType.STRING)
    @Column(name = "review_status", nullable = false, length = 20)
    private ReviewStatus reviewStatus = ReviewStatus.DRAFT;

    @Column(name = "submitted_for_review_by")
    private UUID submittedForReviewBy;

    @Column(name = "submitted_for_review_on")
    private Instant submittedForReviewOn;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "reviewed_on")
    private Instant reviewedOn;

    @Column(name = "review_remarks", columnDefinition = "text")
    private String reviewRemarks;

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

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public BigDecimal getMarks() {
        return marks;
    }

    public void setMarks(BigDecimal marks) {
        this.marks = marks;
    }

    public BigDecimal getNegativeMarks() {
        return negativeMarks;
    }

    public void setNegativeMarks(BigDecimal negativeMarks) {
        this.negativeMarks = negativeMarks;
    }

    public Difficulty getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(Difficulty difficulty) {
        this.difficulty = difficulty;
    }

    public UUID getLanguageId() {
        return languageId;
    }

    public void setLanguageId(UUID languageId) {
        this.languageId = languageId;
    }

    public boolean isPublished() {
        return published;
    }

    public void setPublished(boolean published) {
        this.published = published;
    }

    public UUID getPublishedBy() {
        return publishedBy;
    }

    public void setPublishedBy(UUID publishedBy) {
        this.publishedBy = publishedBy;
    }

    public Instant getPublishedOn() {
        return publishedOn;
    }

    public void setPublishedOn(Instant publishedOn) {
        this.publishedOn = publishedOn;
    }

    public ReviewStatus getReviewStatus() {
        return reviewStatus;
    }

    public void setReviewStatus(ReviewStatus reviewStatus) {
        this.reviewStatus = reviewStatus;
    }

    public UUID getSubmittedForReviewBy() {
        return submittedForReviewBy;
    }

    public void setSubmittedForReviewBy(UUID submittedForReviewBy) {
        this.submittedForReviewBy = submittedForReviewBy;
    }

    public Instant getSubmittedForReviewOn() {
        return submittedForReviewOn;
    }

    public void setSubmittedForReviewOn(Instant submittedForReviewOn) {
        this.submittedForReviewOn = submittedForReviewOn;
    }

    public UUID getReviewedBy() {
        return reviewedBy;
    }

    public void setReviewedBy(UUID reviewedBy) {
        this.reviewedBy = reviewedBy;
    }

    public Instant getReviewedOn() {
        return reviewedOn;
    }

    public void setReviewedOn(Instant reviewedOn) {
        this.reviewedOn = reviewedOn;
    }

    public String getReviewRemarks() {
        return reviewRemarks;
    }

    public void setReviewRemarks(String reviewRemarks) {
        this.reviewRemarks = reviewRemarks;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof QuestionVersion that)) {
            return false;
        }
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
