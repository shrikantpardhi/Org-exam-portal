package com.company.exam.content.entity;

import com.company.exam.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * The logical question identity ({@code specs/12-P3-Content-V1.5.md §10.2, §22}).
 *
 * <p>Taxonomy lives on the logical question. The versioned content lives on
 * {@link QuestionVersion}.
 */
@Entity
@Table(name = "question")
public class Question extends BaseEntity {

    @Column(name = "template_id", nullable = false)
    private UUID templateId;

    @Column(name = "exam_id", nullable = false)
    private UUID examId;

    @Column(name = "subject_id", nullable = false)
    private UUID subjectId;

    @Column(name = "chapter_id")
    private UUID chapterId;

    @Column(name = "topic_id")
    private UUID topicId;

    @Column(name = "sub_topic_id")
    private UUID subTopicId;

    public UUID getTemplateId() {
        return templateId;
    }

    public void setTemplateId(UUID templateId) {
        this.templateId = templateId;
    }

    public UUID getExamId() {
        return examId;
    }

    public void setExamId(UUID examId) {
        this.examId = examId;
    }

    public UUID getSubjectId() {
        return subjectId;
    }

    public void setSubjectId(UUID subjectId) {
        this.subjectId = subjectId;
    }

    public UUID getChapterId() {
        return chapterId;
    }

    public void setChapterId(UUID chapterId) {
        this.chapterId = chapterId;
    }

    public UUID getTopicId() {
        return topicId;
    }

    public void setTopicId(UUID topicId) {
        this.topicId = topicId;
    }

    public UUID getSubTopicId() {
        return subTopicId;
    }

    public void setSubTopicId(UUID subTopicId) {
        this.subTopicId = subTopicId;
    }
}
