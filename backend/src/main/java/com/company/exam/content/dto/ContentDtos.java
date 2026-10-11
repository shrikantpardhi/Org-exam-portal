package com.company.exam.content.dto;

import com.company.exam.common.enums.Status;
import com.company.exam.content.entity.Difficulty;
import com.company.exam.content.entity.ReviewStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Content API DTOs ({@code specs/12-P3-Content-V1.5.md §83}).
 *
 * <p>Authoring DTOs may carry {@code isCorrect}; the student projection
 * ({@link StudentQuestionView}) never does. Write DTOs never expose server-controlled audit values
 * or raw asset file paths.
 */
public final class ContentDtos {

    private ContentDtos() {
    }

    // ----- Question templates -----

    public record TemplateCreateRequest(
            @NotBlank @Size(max = 50) String code,
            @NotBlank @Size(max = 150) String name,
            @Size(max = 2000) String description,
            String configuration) {
    }

    public record TemplateUpdateRequest(
            @Size(max = 150) String name,
            @Size(max = 2000) String description,
            String configuration,
            Status status) {
    }

    public record TemplateResponse(UUID id, String code, String name, String description,
            boolean requiresLatexParsing, Status status) {
    }

    // ----- Questions -----

    public record QuestionCreateRequest(
            @NotNull UUID templateId,
            @NotNull UUID examId,
            @NotNull UUID subjectId,
            UUID chapterId,
            UUID topicId,
            UUID subTopicId,
            @Size(max = 255) String title,
            String content,
            @NotNull @PositiveOrZero BigDecimal marks,
            @PositiveOrZero BigDecimal negativeMarks,
            @NotNull Difficulty difficulty,
            @NotNull UUID languageId) {
    }

    /** Draft-level metadata only; versioned content is changed by creating a new version. */
    public record QuestionUpdateRequest(
            UUID templateId,
            UUID examId,
            UUID subjectId,
            UUID chapterId,
            UUID topicId,
            UUID subTopicId,
            Status status,
            List<UUID> tagIds) {
    }

    public record VersionSummary(UUID id, Integer version, ReviewStatus reviewStatus, boolean published,
            BigDecimal marks, BigDecimal negativeMarks, Difficulty difficulty, Instant publishedOn) {
    }

    public record QuestionResponse(UUID id, UUID templateId, UUID examId, UUID subjectId, UUID chapterId,
            UUID topicId, UUID subTopicId, Status status, List<VersionSummary> versions, List<UUID> tagIds) {
    }

    public record QuestionListItem(UUID id, UUID templateId, UUID examId, UUID subjectId, Difficulty difficulty,
            Integer latestVersion, ReviewStatus latestReviewStatus, boolean published, Instant updatedOn) {
    }

    // ----- Question versions -----

    public record VersionCreateRequest(
            @Size(max = 255) String title,
            String content,
            @NotNull @PositiveOrZero BigDecimal marks,
            @PositiveOrZero BigDecimal negativeMarks,
            @NotNull Difficulty difficulty,
            @NotNull UUID languageId) {
    }

    public record VersionResponse(UUID id, UUID questionId, Integer version, String title, String content,
            String contentFormat, String renderedContent, BigDecimal marks, BigDecimal negativeMarks,
            Difficulty difficulty, UUID languageId, boolean published, ReviewStatus reviewStatus,
            UUID publishedBy, Instant publishedOn, UUID reviewedBy, Instant reviewedOn, String reviewRemarks) {
    }

    public record VersionListItem(UUID id, Integer version, ReviewStatus reviewStatus, boolean published,
            BigDecimal marks, BigDecimal negativeMarks, Difficulty difficulty, Instant publishedOn) {
    }

    public record ReviewRequest(@Size(max = 2000) String remarks) {
    }

    // ----- Options (authoring) -----

    public record OptionCreateRequest(
            @NotBlank @Size(max = 20) String optionKey,
            String content,
            @NotNull Integer displayOrder,
            boolean isCorrect) {
    }

    public record OptionUpdateRequest(String content, Integer displayOrder, Boolean isCorrect) {
    }

    public record OptionResponse(UUID id, UUID questionVersionId, String optionKey, String content,
            Integer displayOrder, boolean isCorrect) {
    }

    /** Student-safe option projection: never includes {@code isCorrect}. */
    public record StudentOptionView(UUID id, String optionKey, Integer displayOrder, String content, String contentFormat) {
    }

    /** Student-safe question-version projection: no {@code isCorrect}, no answer key, no asset paths. */
    public record StudentQuestionView(UUID questionVersionId, String contentFormat, boolean requiresLatexParsing,
            String content, String renderedContent, BigDecimal marks, BigDecimal negativeMarks,
            List<StudentOptionView> options) {
    }

    // ----- Assets -----

    public record AssetResponse(UUID id, UUID questionVersionId, String fileName, String fileType, Status status) {
    }

    // ----- Explanation and hint -----

    public record TextContentRequest(@NotEmpty String content) {
    }

    public record ExplanationResponse(UUID id, UUID questionVersionId, String content, String contentFormat,
            String renderedContent) {
    }

    public record HintResponse(UUID id, UUID questionVersionId, String content, String contentFormat,
            String renderedContent) {
    }

    // ----- Previous exam history -----

    public record PreviousExamCreateRequest(
            @NotNull UUID examId,
            Integer year,
            @Size(max = 50) String shift,
            @Size(max = 100) String source) {
    }

    public record PreviousExamUpdateRequest(Integer year, @Size(max = 50) String shift,
            @Size(max = 100) String source) {
    }

    public record PreviousExamResponse(UUID id, UUID questionId, UUID examId, Integer year, String shift,
            String source) {
    }
}
