package com.company.exam.assessment.mapper;

import com.company.exam.assessment.dto.AssessmentDtos;
import com.company.exam.assessment.entity.Assessment;
import com.company.exam.assessment.entity.AssessmentCollection;
import com.company.exam.assessment.entity.AssessmentQuestion;
import com.company.exam.assessment.entity.AssessmentSchedule;
import com.company.exam.assessment.entity.AssessmentSelectionRule;
import com.company.exam.assessment.entity.AssessmentVersion;
import com.company.exam.assessment.entity.Blueprint;
import com.company.exam.assessment.entity.BlueprintVersion;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Explicit entity-to-DTO mapping for Assessment ({@code specs/13-P4-Assessment-V1.5.md §87}). */
@Component
public class AssessmentMapper {

    private final ObjectMapper objectMapper;

    public AssessmentMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    private JsonNode json(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(value);
        } catch (Exception invalid) {
            return null;
        }
    }

    public AssessmentDtos.BlueprintResponse toResponse(Blueprint blueprint, List<BlueprintVersion> versions) {
        return new AssessmentDtos.BlueprintResponse(blueprint.getId(), blueprint.getCode(), blueprint.getName(),
                blueprint.getDescription(), blueprint.getStatus(),
                versions.stream().map(this::toSummary).toList());
    }

    public AssessmentDtos.BlueprintVersionSummary toSummary(BlueprintVersion version) {
        return new AssessmentDtos.BlueprintVersionSummary(version.getId(), version.getVersion(), version.isPublished(),
                version.getPublishedOn());
    }

    public AssessmentDtos.BlueprintListItem toListItem(Blueprint blueprint) {
        return new AssessmentDtos.BlueprintListItem(blueprint.getId(), blueprint.getCode(), blueprint.getName(),
                blueprint.getStatus(), blueprint.getUpdatedOn());
    }

    public AssessmentDtos.BlueprintVersionResponse toResponse(BlueprintVersion version) {
        return new AssessmentDtos.BlueprintVersionResponse(version.getId(), version.getBlueprintId(),
                version.getVersion(), json(version.getConfiguration()), version.isPublished(), version.getPublishedBy(),
                version.getPublishedOn(), version.getStatus());
    }

    public AssessmentDtos.AssessmentResponse toResponse(Assessment assessment, List<AssessmentVersion> versions) {
        return new AssessmentDtos.AssessmentResponse(assessment.getId(), assessment.getCode(), assessment.getName(),
                assessment.getDescription(), assessment.getStatus(), versions.stream().map(this::toSummary).toList());
    }

    public AssessmentDtos.AssessmentVersionSummary toSummary(AssessmentVersion version) {
        return new AssessmentDtos.AssessmentVersionSummary(version.getId(), version.getVersion(), version.isPublished(),
                version.getEngineType(), version.getDurationMinutes());
    }

    public AssessmentDtos.AssessmentListItem toListItem(Assessment assessment) {
        return new AssessmentDtos.AssessmentListItem(assessment.getId(), assessment.getCode(), assessment.getName(),
                assessment.getStatus(), assessment.getUpdatedOn());
    }

    public AssessmentDtos.AssessmentVersionResponse toResponse(AssessmentVersion version) {
        return new AssessmentDtos.AssessmentVersionResponse(version.getId(), version.getAssessmentId(),
                version.getBlueprintVersionId(), version.getVersion(), version.getTotalQuestions(),
                version.getTotalMarks(), version.getDurationMinutes(), version.getEngineType(),
                version.getEngineConfigurationVersion(), version.getDeliveryPolicyId(), version.isPublished(),
                version.getPublishedBy(), version.getPublishedOn(), version.getStatus());
    }

    public AssessmentDtos.AssessmentQuestionResponse toResponse(AssessmentQuestion question) {
        return new AssessmentDtos.AssessmentQuestionResponse(question.getId(), question.getAssessmentVersionId(),
                question.getQuestionVersionId(), question.getDisplayOrder(), question.getSectionName(),
                question.getMarks(), question.getNegativeMarks());
    }

    public AssessmentDtos.SelectionRuleResponse toResponse(AssessmentSelectionRule rule) {
        return new AssessmentDtos.SelectionRuleResponse(rule.getId(), rule.getAssessmentVersionId(),
                rule.getSectionName(), rule.getTargetCount(), rule.getDisplayOrder(), rule.getMaterializedOn(),
                rule.getMaterializedQuestionCount(), json(rule.getRuleConfiguration()));
    }

    public AssessmentDtos.ScheduleResponse toResponse(AssessmentSchedule schedule) {
        return new AssessmentDtos.ScheduleResponse(schedule.getId(), schedule.getAssessmentVersionId(),
                schedule.getMode(), schedule.getStartTime(), schedule.getEndTime(), schedule.getStatus());
    }

    public AssessmentDtos.CollectionResponse toResponse(AssessmentCollection collection, List<UUID> assessmentIds) {
        return new AssessmentDtos.CollectionResponse(collection.getId(), collection.getCode(), collection.getName(),
                collection.getDescription(), collection.getStatus(), List.copyOf(assessmentIds));
    }
}
