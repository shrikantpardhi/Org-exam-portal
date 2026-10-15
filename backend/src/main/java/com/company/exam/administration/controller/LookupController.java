package com.company.exam.administration.controller;

import com.company.exam.administration.dto.AdministrationDtos;
import com.company.exam.administration.service.LookupService;
import com.company.exam.common.api.ApiResponse;
import com.company.exam.common.enums.Status;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Lookup master data API ({@code specs/20-P11-Administration-V1.5.md §10.3}). */
@RestController
@RequestMapping(path = "/api/v1/lookups", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Administration - Lookups", description = "Reusable configurable master values")
public class LookupController {

    private final LookupService lookupService;

    public LookupController(LookupService lookupService) {
        this.lookupService = lookupService;
    }

    @Operation(summary = "List lookup values")
    @GetMapping
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'LOOKUP_VIEW')")
    public ApiResponse<List<AdministrationDtos.LookupResponse>> list(
            @RequestParam(required = false) String category, @RequestParam(required = false) Status status) {
        return ApiResponse.ok(lookupService.list(category, status));
    }

    @Operation(summary = "Get a lookup value")
    @GetMapping("/{id}")
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'LOOKUP_VIEW')")
    public ApiResponse<AdministrationDtos.LookupResponse> get(@PathVariable UUID id) {
        return ApiResponse.ok(lookupService.get(id));
    }

    @Operation(summary = "Create a lookup value")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'LOOKUP_CREATE')")
    public ApiResponse<AdministrationDtos.LookupResponse> create(
            @Valid @RequestBody AdministrationDtos.CreateLookupRequest request,
            @AuthenticationPrincipal IdentityPrincipal principal) {
        return ApiResponse.ok("Lookup created", lookupService.create(request, principal.userId()));
    }

    @Operation(summary = "Update a lookup value")
    @PutMapping("/{id}")
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'LOOKUP_UPDATE')")
    public ApiResponse<AdministrationDtos.LookupResponse> update(@PathVariable UUID id,
            @Valid @RequestBody AdministrationDtos.UpdateLookupRequest request,
            @AuthenticationPrincipal IdentityPrincipal principal) {
        return ApiResponse.ok("Lookup updated", lookupService.update(id, request, principal.userId()));
    }

    @Operation(summary = "Delete a lookup value (archives when it is an application contract)")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'LOOKUP_DELETE')")
    public void delete(@PathVariable UUID id, @AuthenticationPrincipal IdentityPrincipal principal) {
        lookupService.delete(id, principal.userId());
    }
}
