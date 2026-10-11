package com.company.exam.assessment.controller;

import com.company.exam.assessment.dto.AssessmentDtos;
import com.company.exam.assessment.service.AssessmentCollectionService;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Assessment collection API ({@code specs/13-P4-Assessment-V1.5.md §76}). */
@RestController
@RequestMapping(path = "/api/v1/assessment-collections", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Assessment - Collections", description = "Assessment collections")
public class AssessmentCollectionController {

    private static final Set<String> SORTABLE = Set.of("code", "name", "status", "createdOn", "updatedOn");

    private final AssessmentCollectionService collectionService;

    public AssessmentCollectionController(AssessmentCollectionService collectionService) {
        this.collectionService = collectionService;
    }

    @Operation(summary = "List collections")
    @GetMapping
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'ASSESSMENT_COLLECTION_VIEW')")
    public ApiResponse<List<AssessmentDtos.CollectionResponse>> list(@PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.paged(PageResponse.from(
                collectionService.list(QuerySupport.sanitize(pageable, SORTABLE)), item -> item));
    }

    @Operation(summary = "Get a collection")
    @GetMapping("/{id}")
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'ASSESSMENT_COLLECTION_VIEW')")
    public ApiResponse<AssessmentDtos.CollectionResponse> get(@PathVariable UUID id) {
        return ApiResponse.ok(collectionService.get(id));
    }

    @Operation(summary = "Create a collection")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'ASSESSMENT_COLLECTION_MANAGE')")
    public ApiResponse<AssessmentDtos.CollectionResponse> create(
            @Valid @RequestBody AssessmentDtos.CollectionCreateRequest request,
            @AuthenticationPrincipal IdentityPrincipal principal) {
        return ApiResponse.ok("Collection created", collectionService.create(request, principal.userId()));
    }

    @Operation(summary = "Update a collection")
    @PutMapping("/{id}")
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'ASSESSMENT_COLLECTION_MANAGE')")
    public ApiResponse<AssessmentDtos.CollectionResponse> update(@PathVariable UUID id,
            @Valid @RequestBody AssessmentDtos.CollectionUpdateRequest request,
            @AuthenticationPrincipal IdentityPrincipal principal) {
        return ApiResponse.ok("Collection updated", collectionService.update(id, request, principal.userId()));
    }

    @Operation(summary = "Add an assessment to a collection")
    @PostMapping("/{collectionId}/assessments")
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'ASSESSMENT_COLLECTION_MANAGE')")
    public ApiResponse<AssessmentDtos.CollectionResponse> addAssessment(@PathVariable UUID collectionId,
            @Valid @RequestBody AssessmentDtos.CollectionAssessmentRequest request,
            @AuthenticationPrincipal IdentityPrincipal principal) {
        return ApiResponse.ok("Assessment added to collection",
                collectionService.addAssessment(collectionId, request.assessmentId(), principal.userId()));
    }

    @Operation(summary = "Remove an assessment from a collection")
    @DeleteMapping("/{collectionId}/assessments/{assessmentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'ASSESSMENT_COLLECTION_MANAGE')")
    public void removeAssessment(@PathVariable UUID collectionId, @PathVariable UUID assessmentId,
            @AuthenticationPrincipal IdentityPrincipal principal) {
        collectionService.removeAssessment(collectionId, assessmentId, principal.userId());
    }
}
