package com.company.exam.assessment.entity;

import com.company.exam.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * The historical binding between an AssessmentVersion and an exact QuestionVersion
 * ({@code specs/13-P4-Assessment-V1.5.md §5.5, §24, §25}).
 *
 * <p>{@code question_version_id} is an exact version, never a {@code question_id} or "latest".
 */
@Entity
@Table(name = "assessment_question")
public class AssessmentQuestion extends BaseEntity {

    @Column(name = "assessment_version_id", nullable = false)
    private UUID assessmentVersionId;

    @Column(name = "question_version_id", nullable = false)
    private UUID questionVersionId;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    @Column(name = "section_name", length = 150)
    private String sectionName;

    @Column(name = "marks", precision = 8, scale = 2)
    private BigDecimal marks;

    @Column(name = "negative_marks", precision = 8, scale = 2)
    private BigDecimal negativeMarks;

    public UUID getAssessmentVersionId() {
        return assessmentVersionId;
    }

    public void setAssessmentVersionId(UUID assessmentVersionId) {
        this.assessmentVersionId = assessmentVersionId;
    }

    public UUID getQuestionVersionId() {
        return questionVersionId;
    }

    public void setQuestionVersionId(UUID questionVersionId) {
        this.questionVersionId = questionVersionId;
    }

    public Integer getDisplayOrder() {
        return displayOrder;
    }

    public void setDisplayOrder(Integer displayOrder) {
        this.displayOrder = displayOrder;
    }

    public String getSectionName() {
        return sectionName;
    }

    public void setSectionName(String sectionName) {
        this.sectionName = sectionName;
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
}
