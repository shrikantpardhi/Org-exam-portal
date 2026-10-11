package com.company.exam.academic.dto;

import com.company.exam.common.enums.Status;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

/**
 * Academic API DTOs ({@code specs/11-P2-Academic-V1.5.md §45}).
 *
 * <p>Grouped by resource. Write DTOs never expose {@code createdBy}/{@code createdOn}/
 * {@code updatedBy}/{@code updatedOn}; the backend controls them. Parents are referenced by id,
 * never as nested client-controlled entities. No JPA entity is exposed.
 */
public final class AcademicDtos {

    private AcademicDtos() {
    }

    // ----- Exam -----

    public record ExamCreateRequest(
            @NotBlank @Size(max = 50) String code,
            @NotBlank @Size(max = 150) String name,
            @Size(max = 20000) String description) {
    }

    public record ExamUpdateRequest(
            @Size(max = 150) String name,
            @Size(max = 20000) String description,
            Status status) {
    }

    public record ExamResponse(UUID id, String code, String name, String description, Status status,
            Instant createdOn, Instant updatedOn) {
    }

    public record ExamListItem(UUID id, String code, String name, Status status, Instant updatedOn) {
    }

    // ----- Subject -----

    public record SubjectCreateRequest(
            @NotNull UUID examId,
            @NotBlank @Size(max = 50) String code,
            @NotBlank @Size(max = 150) String name,
            @Size(max = 20000) String description) {
    }

    public record SubjectUpdateRequest(
            @Size(max = 150) String name,
            @Size(max = 20000) String description,
            Status status) {
    }

    public record SubjectResponse(UUID id, UUID examId, String code, String name, String description,
            Status status, Instant createdOn, Instant updatedOn) {
    }

    public record SubjectListItem(UUID id, UUID examId, String code, String name, Status status, Instant updatedOn) {
    }

    // ----- Chapter -----

    public record ChapterCreateRequest(
            @NotNull UUID subjectId,
            @NotBlank @Size(max = 50) String code,
            @NotBlank @Size(max = 150) String name,
            @Size(max = 20000) String description) {
    }

    public record ChapterUpdateRequest(
            @Size(max = 150) String name,
            @Size(max = 20000) String description,
            Status status) {
    }

    public record ChapterResponse(UUID id, UUID subjectId, String code, String name, String description,
            Status status, Instant createdOn, Instant updatedOn) {
    }

    public record ChapterListItem(UUID id, UUID subjectId, String code, String name, Status status, Instant updatedOn) {
    }

    // ----- Topic -----

    public record TopicCreateRequest(
            @NotNull UUID chapterId,
            @NotBlank @Size(max = 50) String code,
            @NotBlank @Size(max = 150) String name,
            @Size(max = 20000) String description) {
    }

    public record TopicUpdateRequest(
            @Size(max = 150) String name,
            @Size(max = 20000) String description,
            Status status) {
    }

    public record TopicResponse(UUID id, UUID chapterId, String code, String name, String description,
            Status status, Instant createdOn, Instant updatedOn) {
    }

    public record TopicListItem(UUID id, UUID chapterId, String code, String name, Status status, Instant updatedOn) {
    }

    // ----- SubTopic -----

    public record SubTopicCreateRequest(
            @NotNull UUID topicId,
            @NotBlank @Size(max = 50) String code,
            @NotBlank @Size(max = 150) String name,
            @Size(max = 20000) String description) {
    }

    public record SubTopicUpdateRequest(
            @Size(max = 150) String name,
            @Size(max = 20000) String description,
            Status status) {
    }

    public record SubTopicResponse(UUID id, UUID topicId, String code, String name, String description,
            Status status, Instant createdOn, Instant updatedOn) {
    }

    public record SubTopicListItem(UUID id, UUID topicId, String code, String name, Status status, Instant updatedOn) {
    }

    // ----- Language -----

    public record LanguageCreateRequest(
            @NotBlank @Size(max = 20) String code,
            @NotBlank @Size(max = 100) String name) {
    }

    public record LanguageUpdateRequest(
            @Size(max = 100) String name,
            Status status) {
    }

    public record LanguageResponse(UUID id, String code, String name, Status status, Instant createdOn,
            Instant updatedOn) {
    }

    public record LanguageListItem(UUID id, String code, String name, Status status, Instant updatedOn) {
    }

    // ----- Tag -----

    public record TagCreateRequest(
            @NotBlank @Size(max = 50) String code,
            @NotBlank @Size(max = 100) String name,
            @Size(max = 20000) String description) {
    }

    public record TagUpdateRequest(
            @Size(max = 100) String name,
            @Size(max = 20000) String description,
            Status status) {
    }

    public record TagResponse(UUID id, String code, String name, String description, Status status, Instant createdOn,
            Instant updatedOn) {
    }

    public record TagListItem(UUID id, String code, String name, Status status, Instant updatedOn) {
    }
}
