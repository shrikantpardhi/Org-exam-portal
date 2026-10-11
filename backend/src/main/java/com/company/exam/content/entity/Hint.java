package com.company.exam.content.entity;

import com.company.exam.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

/** A version-bound, optional hint ({@code specs/12-P3-Content-V1.5.md §10.7, §40}). */
@Entity
@Table(name = "hint")
public class Hint extends BaseEntity {

    @Column(name = "question_version_id", nullable = false)
    private UUID questionVersionId;

    @Column(name = "content", columnDefinition = "text")
    private String content;

    public UUID getQuestionVersionId() {
        return questionVersionId;
    }

    public void setQuestionVersionId(UUID questionVersionId) {
        this.questionVersionId = questionVersionId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
