package com.company.exam.academic.controller;

import com.company.exam.academic.dto.AcademicDtos;
import com.company.exam.academic.service.SubjectService;
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

/** Subject API ({@code specs/11-P2-Academic-V1.5.md §32}). Required filter: {@code examId}. */
@RestController
@RequestMapping(path = "/api/v1/subjects", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Academic - Subjects", description = "Subjects within an exam")
public class SubjectController {

    private static final Set<String> SORTABLE = Set.of("code", "name", "status", "createdOn", "updatedOn");

    private final SubjectService subjectService;

    public SubjectController(SubjectService subjectService) {
        this.subjectService = subjectService;
    }

    @Operation(summary = "List subjects of an exam (examId required)")
    @GetMapping
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'SUBJECT_VIEW')")
    public ApiResponse<List<AcademicDtos.SubjectListItem>> list(
            @RequestParam(required = false) UUID examId,
            @RequestParam(required = false) Status status,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<AcademicDtos.SubjectListItem> page =
                subjectService.list(examId, status, search, QuerySupport.sanitize(pageable, SORTABLE));
        return ApiResponse.paged(PageResponse.from(page, item -> item));
    }

    @Operation(summary = "Get a subject")
    @GetMapping("/{id}")
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'SUBJECT_VIEW')")
    public ApiResponse<AcademicDtos.SubjectResponse> get(@PathVariable UUID id) {
        return ApiResponse.ok(subjectService.get(id));
    }

    @Operation(summary = "Create a subject")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'SUBJECT_CREATE')")
    public ApiResponse<AcademicDtos.SubjectResponse> create(
            @Valid @RequestBody AcademicDtos.SubjectCreateRequest request,
            @AuthenticationPrincipal IdentityPrincipal principal) {
        return ApiResponse.ok("Subject created", subjectService.create(request, principal.userId()));
    }

    @Operation(summary = "Update a subject")
    @PutMapping("/{id}")
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'SUBJECT_UPDATE')")
    public ApiResponse<AcademicDtos.SubjectResponse> update(@PathVariable UUID id,
            @Valid @RequestBody AcademicDtos.SubjectUpdateRequest request,
            @AuthenticationPrincipal IdentityPrincipal principal) {
        return ApiResponse.ok("Subject updated", subjectService.update(id, request, principal.userId()));
    }

    @Operation(summary = "Archive a subject")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'SUBJECT_ARCHIVE')")
    public void archive(@PathVariable UUID id, @AuthenticationPrincipal IdentityPrincipal principal) {
        subjectService.archive(id, principal.userId());
    }
}
