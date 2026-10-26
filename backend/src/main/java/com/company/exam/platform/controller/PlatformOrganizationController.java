package com.company.exam.platform.controller;

import com.company.exam.common.api.ApiResponse;
import com.company.exam.common.enums.Status;
import com.company.exam.common.web.QuerySupport;
import com.company.exam.identity.security.IdentityPrincipal;
import com.company.exam.platform.dto.PlatformDtos;
import com.company.exam.platform.service.PlatformOrganizationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * SUPER_ADMIN business-organization endpoints ({@code specs/05 §72.2-§72.5}).
 */
@RestController
@RequestMapping(path = "/api/v1/platform/organizations", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Platform - Organizations", description = "SUPER_ADMIN business organization lifecycle")
public class PlatformOrganizationController {

    private static final Set<String> SORTABLE = Set.of("code", "name", "status", "createdOn");

    private final PlatformOrganizationService organizationService;

    public PlatformOrganizationController(PlatformOrganizationService organizationService) {
        this.organizationService = organizationService;
    }

    @Operation(summary = "Create a business organization")
    @PostMapping
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'PLATFORM_ORGANIZATION_CREATE')")
    public ApiResponse<PlatformDtos.OrganizationSummary> create(
            @Valid @RequestBody PlatformDtos.CreateOrganizationRequest request,
            @AuthenticationPrincipal IdentityPrincipal principal) {
        return ApiResponse.ok("Organization created",
                organizationService.createOrganization(request, principal.userId()));
    }

    @Operation(summary = "List business organizations (PLATFORM excluded)")
    @GetMapping
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'PLATFORM_ORGANIZATION_VIEW')")
    public ApiResponse<java.util.List<PlatformDtos.OrganizationSummary>> list(
            @RequestParam(required = false) Status status,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.paged(organizationService.listOrganizations(status, search,
                QuerySupport.sanitize(pageable, SORTABLE)));
    }

    @Operation(summary = "Business organization detail (PLATFORM returns 404)")
    @GetMapping("/{id}")
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'PLATFORM_ORGANIZATION_VIEW')")
    public ApiResponse<PlatformDtos.OrganizationDetail> detail(@PathVariable UUID id) {
        return ApiResponse.ok(organizationService.getOrganization(id));
    }

    @Operation(summary = "Create the organization ADMIN (one per org)")
    @PostMapping("/{id}/admins")
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'PLATFORM_ADMIN_CREATE')")
    public ApiResponse<PlatformDtos.AdminSummary> createAdmin(@PathVariable UUID id,
            @Valid @RequestBody PlatformDtos.CreateAdminRequest request,
            @AuthenticationPrincipal IdentityPrincipal principal) {
        return ApiResponse.ok("Administrator created",
                organizationService.createAdmin(id, request, principal.userId()));
    }
}
