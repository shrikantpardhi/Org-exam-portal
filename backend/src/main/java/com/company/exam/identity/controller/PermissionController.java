package com.company.exam.identity.controller;

import com.company.exam.common.api.ApiResponse;
import com.company.exam.common.api.PageResponse;
import com.company.exam.identity.dto.PermissionDtos;
import com.company.exam.identity.service.PermissionService;
import com.company.exam.common.web.QuerySupport;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Permission catalog API ({@code specs/10-P1-Identity-V1.5.md §52};
 * {@code specs/05-API-Contract-V1.5.md §25}). Permissions are read-only through this API.
 */
@RestController
@RequestMapping(path = "/api/v1/permissions", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Permissions", description = "Controlled permission catalog")
public class PermissionController {

    private static final Set<String> SORTABLE = Set.of("code", "name", "status", "createdOn");

    private final PermissionService permissionService;

    public PermissionController(PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    @Operation(summary = "List permissions")
    @GetMapping
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'PERMISSION_VIEW')")
    public ApiResponse<List<PermissionDtos.PermissionSummary>> list(@PageableDefault(size = 20) Pageable pageable) {
        var page = permissionService.list(QuerySupport.sanitize(pageable, SORTABLE));
        return ApiResponse.paged(PageResponse.from(page, summary -> summary));
    }
}
