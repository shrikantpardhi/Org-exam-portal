package com.company.exam.content.controller;

import com.company.exam.common.api.ApiResponse;
import com.company.exam.content.dto.ContentDtos;
import com.company.exam.content.service.ExplanationService;
import com.company.exam.identity.security.IdentityPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Explanation API ({@code specs/12-P3-Content-V1.5.md §39}). */
@RestController
@RequestMapping(path = "/api/v1/question-versions/{versionId}/explanation",
        produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Content - Explanation", description = "Version-bound explanation")
public class ExplanationController {

    private final ExplanationService explanationService;

    public ExplanationController(ExplanationService explanationService) {
        this.explanationService = explanationService;
    }

    @Operation(summary = "Get the explanation")
    @GetMapping
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'QUESTION_VIEW')")
    public ApiResponse<ContentDtos.ExplanationResponse> get(@PathVariable UUID versionId) {
        return ApiResponse.ok(explanationService.get(versionId));
    }

    @Operation(summary = "Create the explanation")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'QUESTION_EXPLANATION_CREATE')")
    public ApiResponse<ContentDtos.ExplanationResponse> create(@PathVariable UUID versionId,
            @Valid @RequestBody ContentDtos.TextContentRequest request,
            @AuthenticationPrincipal IdentityPrincipal principal) {
        return ApiResponse.ok("Explanation created", explanationService.create(versionId, request, principal.userId()));
    }

    @Operation(summary = "Update the explanation")
    @PutMapping
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'QUESTION_EXPLANATION_UPDATE')")
    public ApiResponse<ContentDtos.ExplanationResponse> update(@PathVariable UUID versionId,
            @Valid @RequestBody ContentDtos.TextContentRequest request,
            @AuthenticationPrincipal IdentityPrincipal principal) {
        return ApiResponse.ok("Explanation updated", explanationService.update(versionId, request, principal.userId()));
    }
}
