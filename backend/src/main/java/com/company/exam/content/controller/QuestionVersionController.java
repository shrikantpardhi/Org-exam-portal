package com.company.exam.content.controller;

import com.company.exam.common.api.ApiResponse;
import com.company.exam.content.dto.ContentDtos;
import com.company.exam.content.service.QuestionPublicationService;
import com.company.exam.content.service.QuestionVersionService;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Question version and review workflow API ({@code specs/12-P3-Content-V1.5.md §88-§91}). */
@RestController
@RequestMapping(path = "/api/v1/questions/{questionId}/versions", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Content - Question versions", description = "Versioning, review, and publication")
public class QuestionVersionController {

    private final QuestionVersionService versionService;
    private final QuestionPublicationService publicationService;

    public QuestionVersionController(QuestionVersionService versionService,
            QuestionPublicationService publicationService) {
        this.versionService = versionService;
        this.publicationService = publicationService;
    }

    @Operation(summary = "List a question's versions")
    @GetMapping
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'QUESTION_VIEW')")
    public ApiResponse<List<ContentDtos.VersionListItem>> list(@PathVariable UUID questionId) {
        return ApiResponse.ok(versionService.list(questionId));
    }

    @Operation(summary = "Create a new draft version")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'QUESTION_CREATE')")
    public ApiResponse<ContentDtos.VersionResponse> create(@PathVariable UUID questionId,
            @Valid @RequestBody ContentDtos.VersionCreateRequest request,
            @AuthenticationPrincipal IdentityPrincipal principal) {
        return ApiResponse.ok("Version created", versionService.create(questionId, request, principal.userId()));
    }

    @Operation(summary = "Get a version")
    @GetMapping("/{versionId}")
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'QUESTION_VIEW')")
    public ApiResponse<ContentDtos.VersionResponse> get(@PathVariable UUID questionId, @PathVariable UUID versionId) {
        return ApiResponse.ok(versionService.get(questionId, versionId));
    }

    @Operation(summary = "Submit a version for review")
    @PostMapping("/{versionId}/submit-review")
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'QUESTION_SUBMIT_REVIEW')")
    public ApiResponse<ContentDtos.VersionResponse> submitReview(@PathVariable UUID questionId,
            @PathVariable UUID versionId, @AuthenticationPrincipal IdentityPrincipal principal) {
        return ApiResponse.ok("Submitted for review",
                versionService.submitForReview(questionId, versionId, principal.userId()));
    }

    @Operation(summary = "Approve a version under review")
    @PostMapping("/{versionId}/approve")
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'QUESTION_REVIEW')")
    public ApiResponse<ContentDtos.VersionResponse> approve(@PathVariable UUID questionId,
            @PathVariable UUID versionId, @Valid @RequestBody(required = false) ContentDtos.ReviewRequest request,
            @AuthenticationPrincipal IdentityPrincipal principal) {
        return ApiResponse.ok("Version approved", versionService.approve(questionId, versionId, request,
                principal.userId()));
    }

    @Operation(summary = "Reject a version under review")
    @PostMapping("/{versionId}/reject")
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'QUESTION_REVIEW')")
    public ApiResponse<ContentDtos.VersionResponse> reject(@PathVariable UUID questionId,
            @PathVariable UUID versionId, @Valid @RequestBody(required = false) ContentDtos.ReviewRequest request,
            @AuthenticationPrincipal IdentityPrincipal principal) {
        return ApiResponse.ok("Version rejected", versionService.reject(questionId, versionId, request,
                principal.userId()));
    }

    @Operation(summary = "Publish an approved version")
    @PostMapping("/{versionId}/publish")
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'QUESTION_PUBLISH')")
    public ApiResponse<ContentDtos.VersionResponse> publish(@PathVariable UUID questionId,
            @PathVariable UUID versionId, @AuthenticationPrincipal IdentityPrincipal principal) {
        return ApiResponse.ok("Version published", publicationService.publish(questionId, versionId, principal.userId()));
    }
}
