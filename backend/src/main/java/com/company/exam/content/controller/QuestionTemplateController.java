package com.company.exam.content.controller;

import com.company.exam.common.api.ApiResponse;
import com.company.exam.common.api.PageResponse;
import com.company.exam.common.web.QuerySupport;
import com.company.exam.content.dto.ContentDtos;
import com.company.exam.content.service.QuestionTemplateService;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Question template API ({@code specs/12-P3-Content-V1.5.md §87}). */
@RestController
@RequestMapping(path = "/api/v1/question-templates", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Content - Question templates", description = "Supported question formats")
public class QuestionTemplateController {

    private static final Set<String> SORTABLE = Set.of("code", "name", "status", "createdOn", "updatedOn");

    private final QuestionTemplateService templateService;

    public QuestionTemplateController(QuestionTemplateService templateService) {
        this.templateService = templateService;
    }

    @Operation(summary = "List question templates")
    @GetMapping
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'QUESTION_TEMPLATE_VIEW')")
    public ApiResponse<List<ContentDtos.TemplateResponse>> list(@PageableDefault(size = 20) Pageable pageable) {
        Page<ContentDtos.TemplateResponse> page = templateService.list(QuerySupport.sanitize(pageable, SORTABLE));
        return ApiResponse.paged(PageResponse.from(page, item -> item));
    }

    @Operation(summary = "Get a question template")
    @GetMapping("/{id}")
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'QUESTION_TEMPLATE_VIEW')")
    public ApiResponse<ContentDtos.TemplateResponse> get(@PathVariable UUID id) {
        return ApiResponse.ok(templateService.get(id));
    }

    @Operation(summary = "Create a question template")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'QUESTION_TEMPLATE_CREATE')")
    public ApiResponse<ContentDtos.TemplateResponse> create(
            @Valid @RequestBody ContentDtos.TemplateCreateRequest request,
            @AuthenticationPrincipal IdentityPrincipal principal) {
        return ApiResponse.ok("Question template created", templateService.create(request, principal.userId()));
    }

    @Operation(summary = "Update a question template")
    @PutMapping("/{id}")
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'QUESTION_TEMPLATE_UPDATE')")
    public ApiResponse<ContentDtos.TemplateResponse> update(@PathVariable UUID id,
            @Valid @RequestBody ContentDtos.TemplateUpdateRequest request,
            @AuthenticationPrincipal IdentityPrincipal principal) {
        return ApiResponse.ok("Question template updated", templateService.update(id, request, principal.userId()));
    }
}
