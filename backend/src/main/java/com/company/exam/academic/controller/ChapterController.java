package com.company.exam.academic.controller;

import com.company.exam.academic.dto.AcademicDtos;
import com.company.exam.academic.service.ChapterService;
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

/** Chapter API ({@code specs/11-P2-Academic-V1.5.md §33}). Required filter: {@code subjectId}. */
@RestController
@RequestMapping(path = "/api/v1/chapters", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Academic - Chapters", description = "Chapters within a subject")
public class ChapterController {

    private static final Set<String> SORTABLE = Set.of("code", "name", "status", "createdOn", "updatedOn");

    private final ChapterService chapterService;

    public ChapterController(ChapterService chapterService) {
        this.chapterService = chapterService;
    }

    @Operation(summary = "List chapters of a subject (subjectId required)")
    @GetMapping
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'CHAPTER_VIEW')")
    public ApiResponse<List<AcademicDtos.ChapterListItem>> list(
            @RequestParam(required = false) UUID subjectId,
            @RequestParam(required = false) Status status,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<AcademicDtos.ChapterListItem> page =
                chapterService.list(subjectId, status, search, QuerySupport.sanitize(pageable, SORTABLE));
        return ApiResponse.paged(PageResponse.from(page, item -> item));
    }

    @Operation(summary = "Get a chapter")
    @GetMapping("/{id}")
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'CHAPTER_VIEW')")
    public ApiResponse<AcademicDtos.ChapterResponse> get(@PathVariable UUID id) {
        return ApiResponse.ok(chapterService.get(id));
    }

    @Operation(summary = "Create a chapter")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'CHAPTER_CREATE')")
    public ApiResponse<AcademicDtos.ChapterResponse> create(
            @Valid @RequestBody AcademicDtos.ChapterCreateRequest request,
            @AuthenticationPrincipal IdentityPrincipal principal) {
        return ApiResponse.ok("Chapter created", chapterService.create(request, principal.userId()));
    }

    @Operation(summary = "Update a chapter")
    @PutMapping("/{id}")
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'CHAPTER_UPDATE')")
    public ApiResponse<AcademicDtos.ChapterResponse> update(@PathVariable UUID id,
            @Valid @RequestBody AcademicDtos.ChapterUpdateRequest request,
            @AuthenticationPrincipal IdentityPrincipal principal) {
        return ApiResponse.ok("Chapter updated", chapterService.update(id, request, principal.userId()));
    }

    @Operation(summary = "Archive a chapter")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'CHAPTER_ARCHIVE')")
    public void archive(@PathVariable UUID id, @AuthenticationPrincipal IdentityPrincipal principal) {
        chapterService.archive(id, principal.userId());
    }
}
