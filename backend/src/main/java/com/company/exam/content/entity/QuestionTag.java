package com.company.exam.content.entity;

import com.company.exam.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * Question-to-tag association, owned by Content ({@code specs/12-P3-Content-V1.5.md §10.9, §49}).
 *
 * <p>Protected by {@code UNIQUE(question_id, tag_id)}. Tag remains Academic master data.
 */
@Entity
@Table(name = "question_tag")
public class QuestionTag extends BaseEntity {

    @Column(name = "question_id", nullable = false)
    private UUID questionId;

    @Column(name = "tag_id", nullable = false)
    private UUID tagId;

    public UUID getQuestionId() {
        return questionId;
    }

    public void setQuestionId(UUID questionId) {
        this.questionId = questionId;
    }

    public UUID getTagId() {
        return tagId;
    }

    public void setTagId(UUID tagId) {
        this.tagId = tagId;
    }
}
