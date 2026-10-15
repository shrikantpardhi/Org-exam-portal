package com.company.exam.administration.controller;

import com.company.exam.administration.dto.AdministrationDtos;
import com.company.exam.administration.service.GlobalSearchService;
import com.company.exam.common.api.ApiResponse;
import com.company.exam.identity.service.AuthorizationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Global search ({@code specs/20-P11-Administration-V1.5.md §37-§40}).
 *
 * <p>Requires {@code GLOBAL_SEARCH}; each resource additionally requires its own permission, and
 * results for unpermitted resources are omitted rather than filtered after the fact.
 */
@RestController
@RequestMapping(path = "/api/v1/search", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Administration - Global search", description = "Permission-filtered search")
public class GlobalSearchController {

    private final GlobalSearchService searchService;
    private final AuthorizationService authorizationService;

    public GlobalSearchController(GlobalSearchService searchService, AuthorizationService authorizationService) {
        this.searchService = searchService;
        this.authorizationService = authorizationService;
    }

    @Operation(summary = "Search allow-listed resources")
    @GetMapping
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'GLOBAL_SEARCH')")
    public ApiResponse<AdministrationDtos.SearchResponse> search(
            @RequestParam("q") String query,
            @RequestParam(required = false) String resource,
            @RequestParam(required = false) Integer limit,
            Authentication authentication) {
        return ApiResponse.ok(searchService.search(query, resource, limit, authentication,
                authorizationService::hasPermission));
    }

    @Operation(summary = "List the searchable resources")
    @GetMapping("/resources")
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'GLOBAL_SEARCH')")
    public ApiResponse<List<AdministrationDtos.SupportedResource>> resources() {
        return ApiResponse.ok(searchService.supportedResources());
    }
}
