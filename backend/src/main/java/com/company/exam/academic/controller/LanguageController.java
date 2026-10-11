package com.company.exam.academic.controller;

import com.company.exam.academic.dto.AcademicDtos;
import com.company.exam.academic.service.LanguageService;
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

/** Language API ({@code specs/11-P2-Academic-V1.5.md §36}). Languages are not an enum. */
@RestController
@RequestMapping(path = "/api/v1/languages", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Academic - Languages", description = "Reusable language masters")
public class LanguageController {

    private static final Set<String> SORTABLE = Set.of("code", "name", "status", "createdOn", "updatedOn");

    private final LanguageService languageService;

    public LanguageController(LanguageService languageService) {
        this.languageService = languageService;
    }

    @Operation(summary = "List languages")
    @GetMapping
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'LANGUAGE_VIEW')")
    public ApiResponse<List<AcademicDtos.LanguageListItem>> list(
            @RequestParam(required = false) Status status,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<AcademicDtos.LanguageListItem> page =
                languageService.list(status, search, QuerySupport.sanitize(pageable, SORTABLE));
        return ApiResponse.paged(PageResponse.from(page, item -> item));
    }

    @Operation(summary = "Get a language")
    @GetMapping("/{id}")
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'LANGUAGE_VIEW')")
    public ApiResponse<AcademicDtos.LanguageResponse> get(@PathVariable UUID id) {
        return ApiResponse.ok(languageService.get(id));
    }

    @Operation(summary = "Create a language")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'LANGUAGE_CREATE')")
    public ApiResponse<AcademicDtos.LanguageResponse> create(
            @Valid @RequestBody AcademicDtos.LanguageCreateRequest request,
            @AuthenticationPrincipal IdentityPrincipal principal) {
        return ApiResponse.ok("Language created", languageService.create(request, principal.userId()));
    }

    @Operation(summary = "Update a language")
    @PutMapping("/{id}")
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'LANGUAGE_UPDATE')")
    public ApiResponse<AcademicDtos.LanguageResponse> update(@PathVariable UUID id,
            @Valid @RequestBody AcademicDtos.LanguageUpdateRequest request,
            @AuthenticationPrincipal IdentityPrincipal principal) {
        return ApiResponse.ok("Language updated", languageService.update(id, request, principal.userId()));
    }

    @Operation(summary = "Archive a language")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'LANGUAGE_ARCHIVE')")
    public void archive(@PathVariable UUID id, @AuthenticationPrincipal IdentityPrincipal principal) {
        languageService.archive(id, principal.userId());
    }
}
