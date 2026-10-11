package com.company.exam.content.entity;

import com.company.exam.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * A question option ({@code specs/12-P3-Content-V1.5.md §10.4, §28}).
 *
 * <p>{@code isCorrect} is server-side authoring and evaluation data; it is never returned in a
 * student delivery payload.
 */
@Entity
@Table(name = "question_option")
public class QuestionOption extends BaseEntity {

    @Column(name = "question_version_id", nullable = false)
    private UUID questionVersionId;

    @Column(name = "option_key", nullable = false, length = 20)
    private String optionKey;

    @Column(name = "content", columnDefinition = "text")
    private String content;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    @Column(name = "is_correct", nullable = false)
    private boolean correct;

    public UUID getQuestionVersionId() {
        return questionVersionId;
    }

    public void setQuestionVersionId(UUID questionVersionId) {
        this.questionVersionId = questionVersionId;
    }

    public String getOptionKey() {
        return optionKey;
    }

    public void setOptionKey(String optionKey) {
        this.optionKey = optionKey;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Integer getDisplayOrder() {
        return displayOrder;
    }

    public void setDisplayOrder(Integer displayOrder) {
        this.displayOrder = displayOrder;
    }

    public boolean isCorrect() {
        return correct;
    }

    public void setCorrect(boolean correct) {
        this.correct = correct;
    }
}
