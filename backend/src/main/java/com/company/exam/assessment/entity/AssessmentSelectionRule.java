package com.company.exam.assessment.entity;

import com.company.exam.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A rule-based question selection for a section
 * ({@code specs/13-P4-Assessment-V1.5.md §5.6, §45-§49}).
 *
 * <p>Once materialized, the rule is provenance and is not re-executed at runtime.
 */
@Entity
@Table(name = "assessment_selection_rule")
public class AssessmentSelectionRule extends BaseEntity {

    @Column(name = "assessment_version_id", nullable = false)
    private UUID assessmentVersionId;

    @Column(name = "section_name", nullable = false, length = 150)
    private String sectionName;

    @Column(name = "target_count", nullable = false)
    private Integer targetCount;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "rule_configuration", columnDefinition = "jsonb")
    private String ruleConfiguration;

    @Column(name = "display_order")
    private Integer displayOrder;

    @Column(name = "materialized_on")
    private Instant materializedOn;

    @Column(name = "materialized_question_count")
    private Integer materializedQuestionCount;

    public UUID getAssessmentVersionId() {
        return assessmentVersionId;
    }

    public void setAssessmentVersionId(UUID assessmentVersionId) {
        this.assessmentVersionId = assessmentVersionId;
    }

    public String getSectionName() {
        return sectionName;
    }

    public void setSectionName(String sectionName) {
        this.sectionName = sectionName;
    }

    public Integer getTargetCount() {
        return targetCount;
    }

    public void setTargetCount(Integer targetCount) {
        this.targetCount = targetCount;
    }

    public String getRuleConfiguration() {
        return ruleConfiguration;
    }

    public void setRuleConfiguration(String ruleConfiguration) {
        this.ruleConfiguration = ruleConfiguration;
    }

    public Integer getDisplayOrder() {
        return displayOrder;
    }

    public void setDisplayOrder(Integer displayOrder) {
        this.displayOrder = displayOrder;
    }

    public Instant getMaterializedOn() {
        return materializedOn;
    }

    public void setMaterializedOn(Instant materializedOn) {
        this.materializedOn = materializedOn;
    }

    public Integer getMaterializedQuestionCount() {
        return materializedQuestionCount;
    }

    public void setMaterializedQuestionCount(Integer materializedQuestionCount) {
        this.materializedQuestionCount = materializedQuestionCount;
    }
}
