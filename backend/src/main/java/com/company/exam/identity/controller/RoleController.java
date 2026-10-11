package com.company.exam.identity.controller;

import com.company.exam.common.api.ApiResponse;
import com.company.exam.common.api.PageResponse;
import com.company.exam.identity.dto.PermissionDtos;
import com.company.exam.identity.dto.RoleDtos;
import com.company.exam.identity.security.IdentityPrincipal;
import com.company.exam.identity.service.RoleService;
import com.company.exam.common.web.QuerySupport;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Role management API ({@code specs/10-P1-Identity-V1.5.md §51, §60-§63};
 * {@code specs/05-API-Contract-V1.5.md §24}).
 */
@RestController
@RequestMapping(path = "/api/v1/roles", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Roles", description = "Role management and role-permission assignment")
public class RoleController {

    private static final Set<String> SORTABLE = Set.of("code", "name", "status", "createdOn");

    private final RoleService roleService;

    public RoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    @Operation(summary = "List roles")
    @GetMapping
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'ROLE_VIEW')")
    public ApiResponse<List<RoleDtos.RoleSummary>> list(@PageableDefault(size = 20) Pageable pageable) {
        Page<RoleDtos.RoleSummary> page = roleService.list(QuerySupport.sanitize(pageable, SORTABLE));
        return ApiResponse.paged(PageResponse.from(page, summary -> summary));
    }

    @Operation(summary = "Create a role")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'ROLE_CREATE')")
    public ApiResponse<RoleDtos.RoleSummary> create(@Valid @RequestBody RoleDtos.CreateRoleRequest request) {
        return ApiResponse.ok("Role created", roleService.create(request));
    }

    @Operation(summary = "Update a role")
    @PutMapping("/{id}")
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'ROLE_UPDATE')")
    public ApiResponse<RoleDtos.RoleSummary> update(@PathVariable UUID id,
            @Valid @RequestBody RoleDtos.UpdateRoleRequest request,
            @AuthenticationPrincipal IdentityPrincipal principal) {
        return ApiResponse.ok("Role updated", roleService.update(id, request, principal.userId()));
    }

    @Operation(summary = "Get a role's effective permissions")
    @GetMapping("/{id}/permissions")
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'ROLE_PERMISSION_VIEW')")
    public ApiResponse<List<PermissionDtos.PermissionSummary>> permissions(@PathVariable UUID id) {
        return ApiResponse.ok("Role permissions retrieved", roleService.permissions(id));
    }

    @Operation(summary = "Replace a role's permissions",
            description = "Transactional full replacement of the role's permission assignments.")
    @PutMapping("/{id}/permissions")
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'ROLE_PERMISSION_ASSIGN')")
    public ApiResponse<List<PermissionDtos.PermissionSummary>> replacePermissions(@PathVariable UUID id,
            @Valid @RequestBody RoleDtos.ReplaceRolePermissionsRequest request,
            @AuthenticationPrincipal IdentityPrincipal principal) {
        return ApiResponse.ok("Role permissions updated",
                roleService.replacePermissions(id, request.permissionIds(), principal.userId()));
    }
}
