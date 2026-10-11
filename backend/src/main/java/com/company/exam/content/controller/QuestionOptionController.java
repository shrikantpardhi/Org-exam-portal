package com.company.exam.content.controller;

import com.company.exam.common.api.ApiResponse;
import com.company.exam.content.dto.ContentDtos;
import com.company.exam.content.service.QuestionOptionService;
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

/** Question option API ({@code specs/12-P3-Content-V1.5.md §38}). */
@RestController
@RequestMapping(path = "/api/v1/questions/{questionId}/versions/{versionId}/options",
        produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Content - Options", description = "Question options (authoring)")
public class QuestionOptionController {

    private final QuestionOptionService optionService;

    public QuestionOptionController(QuestionOptionService optionService) {
        this.optionService = optionService;
    }

    @Operation(summary = "List options")
    @GetMapping
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'QUESTION_VIEW')")
    public ApiResponse<List<ContentDtos.OptionResponse>> list(@PathVariable UUID questionId,
            @PathVariable UUID versionId) {
        return ApiResponse.ok(optionService.list(questionId, versionId));
    }

    @Operation(summary = "Create an option")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'QUESTION_OPTION_CREATE')")
    public ApiResponse<ContentDtos.OptionResponse> create(@PathVariable UUID questionId, @PathVariable UUID versionId,
            @Valid @RequestBody ContentDtos.OptionCreateRequest request,
            @AuthenticationPrincipal IdentityPrincipal principal) {
        return ApiResponse.ok("Option created", optionService.create(questionId, versionId, request, principal.userId()));
    }

    @Operation(summary = "Update an option")
    @PutMapping("/{optionId}")
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'QUESTION_OPTION_UPDATE')")
    public ApiResponse<ContentDtos.OptionResponse> update(@PathVariable UUID questionId, @PathVariable UUID versionId,
            @PathVariable UUID optionId, @Valid @RequestBody ContentDtos.OptionUpdateRequest request,
            @AuthenticationPrincipal IdentityPrincipal principal) {
        return ApiResponse.ok("Option updated",
                optionService.update(questionId, versionId, optionId, request, principal.userId()));
    }

    @Operation(summary = "Delete a draft option")
    @DeleteMapping("/{optionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'QUESTION_OPTION_ARCHIVE')")
    public void delete(@PathVariable UUID questionId, @PathVariable UUID versionId, @PathVariable UUID optionId,
            @AuthenticationPrincipal IdentityPrincipal principal) {
        optionService.delete(questionId, versionId, optionId, principal.userId());
    }
}
