package com.company.exam.assessment.controller;

import com.company.exam.assessment.dto.AssessmentDtos;
import com.company.exam.assessment.service.AssessmentService;
import com.company.exam.assessment.service.AssessmentVersionService;
import com.company.exam.common.api.ApiResponse;
import com.company.exam.common.api.PageResponse;
import com.company.exam.common.enums.Status;
import com.company.exam.common.web.QuerySupport;
import com.company.exam.identity.security.IdentityPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Set;
import java.util.UUID;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Assessment API ({@code specs/13-P4-Assessment-V1.5.md §64, §66}). */
@RestController
@RequestMapping(path = "/api/v1/assessments", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Assessment - Assessments", description = "Logical assessments and versions")
public class AssessmentController {

    private static final Set<String> SORTABLE = Set.of("code", "name", "status", "createdOn", "updatedOn");

    private final AssessmentService assessmentService;
    private final AssessmentVersionService versionService;

    public AssessmentController(AssessmentService assessmentService, AssessmentVersionService versionService) {
        this.assessmentService = assessmentService;
        this.versionService = versionService;
    }

    @Operation(summary = "List assessments")
    @GetMapping
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'ASSESSMENT_VIEW')")
    public ApiResponse<List<AssessmentDtos.AssessmentListItem>> list(
            @RequestParam(required = false) Status status,
            @RequestParam(required = false) UUID blueprintId,
            @RequestParam(required = false) UUID examId,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.paged(PageResponse.from(
                assessmentService.list(status, blueprintId, examId, search, QuerySupport.sanitize(pageable, SORTABLE)),
                item -> item));
    }

    @Operation(summary = "Get an assessment")
    @GetMapping("/{id}")
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'ASSESSMENT_VIEW')")
    public ApiResponse<AssessmentDtos.AssessmentResponse> get(@PathVariable UUID id) {
        return ApiResponse.ok(assessmentService.get(id));
    }

    @Operation(summary = "Create an assessment")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'ASSESSMENT_CREATE')")
    public ApiResponse<AssessmentDtos.AssessmentResponse> create(
            @Valid @RequestBody AssessmentDtos.AssessmentCreateRequest request,
            @AuthenticationPrincipal IdentityPrincipal principal) {
        return ApiResponse.ok("Assessment created", assessmentService.create(request, principal.userId()));
    }

    @Operation(summary = "Update an assessment")
    @PutMapping("/{id}")
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'ASSESSMENT_UPDATE')")
    public ApiResponse<AssessmentDtos.AssessmentResponse> update(@PathVariable UUID id,
            @Valid @RequestBody AssessmentDtos.AssessmentUpdateRequest request,
            @AuthenticationPrincipal IdentityPrincipal principal) {
        return ApiResponse.ok("Assessment updated", assessmentService.update(id, request, principal.userId()));
    }

    @Operation(summary = "List assessment versions")
    @GetMapping("/{id}/versions")
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'ASSESSMENT_VERSION_VIEW')")
    public ApiResponse<List<AssessmentDtos.AssessmentVersionResponse>> listVersions(@PathVariable UUID id) {
        return ApiResponse.ok(versionService.list(id));
    }

    @Operation(summary = "Create an assessment version")
    @PostMapping("/{id}/versions")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'ASSESSMENT_VERSION_CREATE')")
    public ApiResponse<AssessmentDtos.AssessmentVersionResponse> createVersion(@PathVariable UUID id,
            @Valid @RequestBody AssessmentDtos.AssessmentVersionCreateRequest request,
            @AuthenticationPrincipal IdentityPrincipal principal) {
        return ApiResponse.ok("Assessment version created", versionService.create(id, request, principal.userId()));
    }

    @Operation(summary = "Get an assessment version")
    @GetMapping("/{id}/versions/{versionId}")
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'ASSESSMENT_VERSION_VIEW')")
    public ApiResponse<AssessmentDtos.AssessmentVersionResponse> getVersion(@PathVariable UUID id,
            @PathVariable UUID versionId) {
        return ApiResponse.ok(versionService.get(id, versionId));
    }
}
