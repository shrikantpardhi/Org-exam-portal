package com.company.exam.content.controller;

import com.company.exam.common.api.ApiResponse;
import com.company.exam.common.api.PageResponse;
import com.company.exam.common.enums.Status;
import com.company.exam.common.web.QuerySupport;
import com.company.exam.content.dto.ContentDtos;
import com.company.exam.content.entity.Difficulty;
import com.company.exam.content.entity.ReviewStatus;
import com.company.exam.content.service.QuestionService;
import com.company.exam.identity.security.IdentityPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Question API ({@code specs/12-P3-Content-V1.5.md §88, §93, §94}). */
@RestController
@RequestMapping(path = "/api/v1/questions", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Content - Questions", description = "Logical questions and taxonomy mapping")
public class QuestionController {

    private static final Set<String> SORTABLE = Set.of("createdOn", "updatedOn");

    private final QuestionService questionService;

    public QuestionController(QuestionService questionService) {
        this.questionService = questionService;
    }

    @Operation(summary = "List questions")
    @GetMapping
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'QUESTION_VIEW')")
    public ApiResponse<List<ContentDtos.QuestionListItem>> list(
            @RequestParam(required = false) Status status,
            @RequestParam(required = false) UUID templateId,
            @RequestParam(required = false) UUID examId,
            @RequestParam(required = false) UUID subjectId,
            @RequestParam(required = false) UUID chapterId,
            @RequestParam(required = false) UUID topicId,
            @RequestParam(required = false) UUID tagId,
            @RequestParam(required = false) UUID languageId,
            @RequestParam(required = false) Difficulty difficulty,
            @RequestParam(required = false) Boolean isPublished,
            @RequestParam(required = false) ReviewStatus reviewStatus,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<ContentDtos.QuestionListItem> page = questionService.list(status, templateId, examId, subjectId, chapterId,
                topicId, tagId, languageId, difficulty, isPublished, reviewStatus, search,
                QuerySupport.sanitize(pageable, SORTABLE));
        return ApiResponse.paged(PageResponse.from(page, item -> item));
    }

    @Operation(summary = "Get a question")
    @GetMapping("/{id}")
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'QUESTION_VIEW')")
    public ApiResponse<ContentDtos.QuestionResponse> get(@PathVariable UUID id) {
        return ApiResponse.ok(questionService.get(id));
    }

    @Operation(summary = "Create a question with its initial version")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'QUESTION_CREATE')")
    public ApiResponse<ContentDtos.QuestionResponse> create(
            @Valid @RequestBody ContentDtos.QuestionCreateRequest request,
            @AuthenticationPrincipal IdentityPrincipal principal) {
        return ApiResponse.ok("Question created", questionService.create(request, principal.userId()));
    }

    @Operation(summary = "Update draft-level question metadata")
    @PutMapping("/{id}")
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'QUESTION_UPDATE')")
    public ApiResponse<ContentDtos.QuestionResponse> update(@PathVariable UUID id,
            @Valid @RequestBody ContentDtos.QuestionUpdateRequest request,
            @AuthenticationPrincipal IdentityPrincipal principal) {
        return ApiResponse.ok("Question updated", questionService.update(id, request, principal.userId()));
    }

    @Operation(summary = "Archive a question")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'QUESTION_ARCHIVE')")
    public void archive(@PathVariable UUID id, @AuthenticationPrincipal IdentityPrincipal principal) {
        questionService.archive(id, principal.userId());
    }
}
