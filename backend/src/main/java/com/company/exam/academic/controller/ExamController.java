package com.company.exam.academic.controller;

import com.company.exam.academic.dto.AcademicDtos;
import com.company.exam.academic.service.ExamService;
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

/** Exam API ({@code specs/11-P2-Academic-V1.5.md §31}; {@code specs/05-API-Contract-V1.5.md §27.1}). */
@RestController
@RequestMapping(path = "/api/v1/exams", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Academic - Exams", description = "Examination families")
public class ExamController {

    private static final Set<String> SORTABLE = Set.of("code", "name", "status", "createdOn", "updatedOn");

    private final ExamService examService;

    public ExamController(ExamService examService) {
        this.examService = examService;
    }

    @Operation(summary = "List exams")
    @GetMapping
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'EXAM_VIEW')")
    public ApiResponse<List<AcademicDtos.ExamListItem>> list(
            @RequestParam(required = false) Status status,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<AcademicDtos.ExamListItem> page = examService.list(status, search, QuerySupport.sanitize(pageable, SORTABLE));
        return ApiResponse.paged(PageResponse.from(page, item -> item));
    }

    @Operation(summary = "Get an exam")
    @GetMapping("/{id}")
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'EXAM_VIEW')")
    public ApiResponse<AcademicDtos.ExamResponse> get(@PathVariable UUID id) {
        return ApiResponse.ok(examService.get(id));
    }

    @Operation(summary = "Create an exam")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'EXAM_CREATE')")
    public ApiResponse<AcademicDtos.ExamResponse> create(@Valid @RequestBody AcademicDtos.ExamCreateRequest request,
            @AuthenticationPrincipal IdentityPrincipal principal) {
        return ApiResponse.ok("Exam created", examService.create(request, principal.userId()));
    }

    @Operation(summary = "Update an exam")
    @PutMapping("/{id}")
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'EXAM_UPDATE')")
    public ApiResponse<AcademicDtos.ExamResponse> update(@PathVariable UUID id,
            @Valid @RequestBody AcademicDtos.ExamUpdateRequest request,
            @AuthenticationPrincipal IdentityPrincipal principal) {
        return ApiResponse.ok("Exam updated", examService.update(id, request, principal.userId()));
    }

    @Operation(summary = "Archive an exam")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'EXAM_ARCHIVE')")
    public void archive(@PathVariable UUID id, @AuthenticationPrincipal IdentityPrincipal principal) {
        examService.archive(id, principal.userId());
    }
}
