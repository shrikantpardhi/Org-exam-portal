package com.company.exam.assessment.dto;

import com.company.exam.assessment.entity.EngineType;
import com.company.exam.assessment.entity.ScheduleMode;
import com.company.exam.common.enums.Status;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

/**
 * Blueprint and Assessment API DTOs ({@code specs/13-P4-Assessment-V1.5.md §87}).
 *
 * <p>Write DTOs never expose server-controlled audit values. JPA entities are not returned.
 */
public final class AssessmentDtos {

    private AssessmentDtos() {
    }

    // ----- Blueprint -----

    public record BlueprintCreateRequest(
            @NotBlank @Size(max = 50) String code,
            @NotBlank @Size(max = 150) String name,
            @Size(max = 2000) String description) {
    }

    public record BlueprintUpdateRequest(@Size(max = 150) String name, @Size(max = 2000) String description,
            Status status) {
    }

    public record BlueprintVersionSummary(UUID id, Integer version, boolean published, Instant publishedOn) {
    }

    public record BlueprintResponse(UUID id, String code, String name, String description, Status status,
            List<BlueprintVersionSummary> versions) {
    }

    public record BlueprintListItem(UUID id, String code, String name, Status status, Instant updatedOn) {
    }

    public record BlueprintVersionCreateRequest(@NotNull JsonNode configuration) {
    }

    public record BlueprintVersionUpdateRequest(@NotNull JsonNode configuration) {
    }

    public record BlueprintVersionResponse(UUID id, UUID blueprintId, Integer version, JsonNode configuration,
            boolean published, UUID publishedBy, Instant publishedOn, Status status) {
    }

    // ----- Assessment -----

    public record AssessmentCreateRequest(
            @NotBlank @Size(max = 50) String code,
            @NotBlank @Size(max = 200) String name,
            @Size(max = 2000) String description) {
    }

    public record AssessmentUpdateRequest(@Size(max = 200) String name, @Size(max = 2000) String description,
            Status status) {
    }

    public record AssessmentVersionSummary(UUID id, Integer version, boolean published, EngineType engineType,
            Integer durationMinutes) {
    }

    public record AssessmentResponse(UUID id, String code, String name, String description, Status status,
            List<AssessmentVersionSummary> versions) {
    }

    public record AssessmentListItem(UUID id, String code, String name, Status status, Instant updatedOn) {
    }

    public record AssessmentVersionCreateRequest(
            @NotNull UUID blueprintVersionId,
            @Positive Integer durationMinutes,
            EngineType engineType,
            UUID deliveryPolicyId) {
    }

    public record AssessmentVersionUpdateRequest(EngineType engineType, UUID deliveryPolicyId,
            Integer durationMinutes) {
    }

    public record AssessmentVersionResponse(UUID id, UUID assessmentId, UUID blueprintVersionId, Integer version,
            Integer totalQuestions, BigDecimal totalMarks, Integer durationMinutes, EngineType engineType,
            Integer engineConfigurationVersion, UUID deliveryPolicyId, boolean published, UUID publishedBy,
            Instant publishedOn, Status status) {
    }

    // ----- AssessmentQuestion -----

    public record AddAssessmentQuestionRequest(
            @NotNull UUID questionVersionId,
            @NotNull Integer displayOrder,
            @NotBlank @Size(max = 150) String sectionName,
            @NotNull @PositiveOrZero BigDecimal marks,
            @NotNull @PositiveOrZero BigDecimal negativeMarks) {
    }

    public record UpdateAssessmentQuestionRequest(Integer displayOrder, @Size(max = 150) String sectionName,
            @PositiveOrZero BigDecimal marks, @PositiveOrZero BigDecimal negativeMarks) {
    }

    public record QuestionOrder(@NotNull UUID assessmentQuestionId, @NotNull Integer displayOrder) {
    }

    public record ReorderRequest(@NotEmpty List<QuestionOrder> questionOrders) {
    }

    public record AssessmentQuestionResponse(UUID id, UUID assessmentVersionId, UUID questionVersionId,
            Integer displayOrder, String sectionName, BigDecimal marks, BigDecimal negativeMarks) {
    }

    // ----- Selection rules -----

    public record SelectionRuleCreateRequest(
            @NotBlank @Size(max = 150) String sectionName,
            @NotNull @Positive Integer targetCount,
            @NotNull Integer displayOrder,
            @NotNull JsonNode ruleConfiguration) {
    }

    public record SelectionRuleUpdateRequest(@Positive Integer targetCount, Integer displayOrder,
            JsonNode ruleConfiguration) {
    }

    public record SelectionRuleResponse(UUID id, UUID assessmentVersionId, String sectionName, Integer targetCount,
            Integer displayOrder, Instant materializedOn, Integer materializedQuestionCount, JsonNode ruleConfiguration) {
    }

    public record MaterializeRequest(@NotBlank String mode) {
    }

    // ----- Validation and preview -----

    public record ValidationIssue(String code, String message) {
    }

    public record ValidationResult(boolean valid, List<ValidationIssue> errors) {
    }

    public record PreviewQuestion(UUID assessmentQuestionId, UUID questionVersionId, Integer displayOrder,
            BigDecimal marks, BigDecimal negativeMarks, String contentFormat, String content, String renderedContent) {
    }

    public record PreviewSection(String sectionName, List<PreviewQuestion> questions) {
    }

    public record PreviewResponse(UUID assessmentVersionId, Integer totalQuestions, BigDecimal totalMarks,
            Integer durationMinutes, EngineType engineType, List<PreviewSection> sections) {
    }

    // ----- Schedule -----

    public record ScheduleCreateRequest(
            @NotNull UUID assessmentVersionId,
            @NotNull ScheduleMode mode,
            @NotNull Instant startTime,
            @NotNull Instant endTime) {
    }

    public record ScheduleUpdateRequest(@NotNull Instant startTime, @NotNull Instant endTime) {
    }

    public record ScheduleResponse(UUID id, UUID assessmentVersionId, ScheduleMode mode, Instant startTime,
            Instant endTime, Status status) {
    }

    // ----- Collection -----

    public record CollectionCreateRequest(
            @NotBlank @Size(max = 50) String code,
            @NotBlank @Size(max = 200) String name,
            @Size(max = 2000) String description) {
    }

    public record CollectionUpdateRequest(@Size(max = 200) String name, @Size(max = 2000) String description,
            Status status) {
    }

    public record CollectionAssessmentRequest(@NotNull UUID assessmentId) {
    }

    public record CollectionResponse(UUID id, String code, String name, String description, Status status,
            List<UUID> assessmentIds) {
    }
}
