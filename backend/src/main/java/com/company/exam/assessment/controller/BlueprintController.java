package com.company.exam.assessment.controller;

import com.company.exam.assessment.dto.AssessmentDtos;
import com.company.exam.assessment.service.BlueprintPublicationService;
import com.company.exam.assessment.service.BlueprintService;
import com.company.exam.assessment.service.BlueprintVersionService;
import com.company.exam.common.api.ApiResponse;
import com.company.exam.common.api.PageResponse;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Blueprint API ({@code specs/13-P4-Assessment-V1.5.md §65}). */
@RestController
@RequestMapping(path = "/api/v1/blueprints", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Assessment - Blueprints", description = "Blueprints and blueprint versions")
public class BlueprintController {

    private static final Set<String> SORTABLE = Set.of("code", "name", "status", "createdOn", "updatedOn");

    private final BlueprintService blueprintService;
    private final BlueprintVersionService versionService;
    private final BlueprintPublicationService publicationService;

    public BlueprintController(BlueprintService blueprintService, BlueprintVersionService versionService,
            BlueprintPublicationService publicationService) {
        this.blueprintService = blueprintService;
        this.versionService = versionService;
        this.publicationService = publicationService;
    }

    @Operation(summary = "List blueprints")
    @GetMapping
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'BLUEPRINT_VIEW')")
    public ApiResponse<List<AssessmentDtos.BlueprintListItem>> list(@PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.paged(PageResponse.from(
                blueprintService.list(QuerySupport.sanitize(pageable, SORTABLE)), item -> item));
    }

    @Operation(summary = "Get a blueprint")
    @GetMapping("/{id}")
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'BLUEPRINT_VIEW')")
    public ApiResponse<AssessmentDtos.BlueprintResponse> get(@PathVariable UUID id) {
        return ApiResponse.ok(blueprintService.get(id));
    }

    @Operation(summary = "Create a blueprint")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'BLUEPRINT_CREATE')")
    public ApiResponse<AssessmentDtos.BlueprintResponse> create(
            @Valid @RequestBody AssessmentDtos.BlueprintCreateRequest request,
            @AuthenticationPrincipal IdentityPrincipal principal) {
        return ApiResponse.ok("Blueprint created", blueprintService.create(request, principal.userId()));
    }

    @Operation(summary = "Update a blueprint")
    @PutMapping("/{id}")
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'BLUEPRINT_UPDATE')")
    public ApiResponse<AssessmentDtos.BlueprintResponse> update(@PathVariable UUID id,
            @Valid @RequestBody AssessmentDtos.BlueprintUpdateRequest request,
            @AuthenticationPrincipal IdentityPrincipal principal) {
        return ApiResponse.ok("Blueprint updated", blueprintService.update(id, request, principal.userId()));
    }

    @Operation(summary = "List blueprint versions")
    @GetMapping("/{id}/versions")
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'BLUEPRINT_VIEW')")
    public ApiResponse<List<AssessmentDtos.BlueprintVersionResponse>> listVersions(@PathVariable UUID id) {
        return ApiResponse.ok(versionService.list(id));
    }

    @Operation(summary = "Create a blueprint version")
    @PostMapping("/{id}/versions")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'BLUEPRINT_UPDATE')")
    public ApiResponse<AssessmentDtos.BlueprintVersionResponse> createVersion(@PathVariable UUID id,
            @Valid @RequestBody AssessmentDtos.BlueprintVersionCreateRequest request,
            @AuthenticationPrincipal IdentityPrincipal principal) {
        return ApiResponse.ok("Blueprint version created", versionService.create(id, request, principal.userId()));
    }

    @Operation(summary = "Get a blueprint version")
    @GetMapping("/{id}/versions/{versionId}")
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'BLUEPRINT_VIEW')")
    public ApiResponse<AssessmentDtos.BlueprintVersionResponse> getVersion(@PathVariable UUID id,
            @PathVariable UUID versionId) {
        return ApiResponse.ok(versionService.get(id, versionId));
    }

    @Operation(summary = "Publish a blueprint version")
    @PostMapping("/{id}/versions/{versionId}/publish")
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'BLUEPRINT_PUBLISH')")
    public ApiResponse<AssessmentDtos.BlueprintVersionResponse> publish(@PathVariable UUID id,
            @PathVariable UUID versionId, @AuthenticationPrincipal IdentityPrincipal principal) {
        return ApiResponse.ok("Blueprint version published",
                publicationService.publish(id, versionId, principal.userId()));
    }
}
