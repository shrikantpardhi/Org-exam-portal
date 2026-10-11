package com.company.exam.content.controller;

import com.company.exam.common.api.ApiResponse;
import com.company.exam.content.dto.ContentDtos;
import com.company.exam.content.service.PreviousExamHistoryService;
import com.company.exam.identity.security.IdentityPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Previous exam history API ({@code specs/12-P3-Content-V1.5.md §44}). */
@RestController
@RequestMapping(path = "/api/v1/questions/{questionId}/previous-exams", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Content - Previous exams", description = "Historical occurrences of a question")
public class PreviousExamHistoryController {

    private final PreviousExamHistoryService historyService;

    public PreviousExamHistoryController(PreviousExamHistoryService historyService) {
        this.historyService = historyService;
    }

    @Operation(summary = "List previous exam occurrences")
    @GetMapping
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'QUESTION_PREVIOUS_EXAM_VIEW')")
    public ApiResponse<List<ContentDtos.PreviousExamResponse>> list(@PathVariable UUID questionId) {
        return ApiResponse.ok(historyService.list(questionId));
    }

    @Operation(summary = "Add a previous exam occurrence")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'QUESTION_PREVIOUS_EXAM_MANAGE')")
    public ApiResponse<ContentDtos.PreviousExamResponse> create(@PathVariable UUID questionId,
            @Valid @RequestBody ContentDtos.PreviousExamCreateRequest request,
            @AuthenticationPrincipal IdentityPrincipal principal) {
        return ApiResponse.ok("Previous exam occurrence added",
                historyService.create(questionId, request, principal.userId()));
    }

    @Operation(summary = "Update a previous exam occurrence")
    @PutMapping("/{historyId}")
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'QUESTION_PREVIOUS_EXAM_MANAGE')")
    public ApiResponse<ContentDtos.PreviousExamResponse> update(@PathVariable UUID questionId,
            @PathVariable UUID historyId, @Valid @RequestBody ContentDtos.PreviousExamUpdateRequest request,
            @AuthenticationPrincipal IdentityPrincipal principal) {
        return ApiResponse.ok("Previous exam occurrence updated",
                historyService.update(questionId, historyId, request, principal.userId()));
    }

    @Operation(summary = "Archive a previous exam occurrence")
    @DeleteMapping("/{historyId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'QUESTION_PREVIOUS_EXAM_MANAGE')")
    public void delete(@PathVariable UUID questionId, @PathVariable UUID historyId,
            @AuthenticationPrincipal IdentityPrincipal principal) {
        historyService.delete(questionId, historyId, principal.userId());
    }
}
