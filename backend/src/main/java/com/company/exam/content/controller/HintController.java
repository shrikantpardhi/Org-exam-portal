package com.company.exam.content.controller;

import com.company.exam.common.api.ApiResponse;
import com.company.exam.content.dto.ContentDtos;
import com.company.exam.content.service.HintService;
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

/** Hint API ({@code specs/12-P3-Content-V1.5.md §40}). */
@RestController
@RequestMapping(path = "/api/v1/question-versions/{versionId}/hint", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Content - Hint", description = "Version-bound optional hint")
public class HintController {

    private final HintService hintService;

    public HintController(HintService hintService) {
        this.hintService = hintService;
    }

    @Operation(summary = "Get the hint")
    @GetMapping
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'QUESTION_VIEW')")
    public ApiResponse<ContentDtos.HintResponse> get(@PathVariable UUID versionId) {
        return ApiResponse.ok(hintService.get(versionId));
    }

    @Operation(summary = "Create the hint")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'QUESTION_HINT_CREATE')")
    public ApiResponse<ContentDtos.HintResponse> create(@PathVariable UUID versionId,
            @Valid @RequestBody ContentDtos.TextContentRequest request,
            @AuthenticationPrincipal IdentityPrincipal principal) {
        return ApiResponse.ok("Hint created", hintService.create(versionId, request, principal.userId()));
    }

    @Operation(summary = "Update the hint")
    @PutMapping
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'QUESTION_HINT_UPDATE')")
    public ApiResponse<ContentDtos.HintResponse> update(@PathVariable UUID versionId,
            @Valid @RequestBody ContentDtos.TextContentRequest request,
            @AuthenticationPrincipal IdentityPrincipal principal) {
        return ApiResponse.ok("Hint updated", hintService.update(versionId, request, principal.userId()));
    }
}
