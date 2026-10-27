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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * SUPER_ADMIN business-organization endpoints ({@code specs/05 §72.2-§72.5}, §72.7-§72.10).
 *
 * <p>§72.10-§72.13 are the administrator-management additions recorded in ADR-0053: list, update roles,
 * and deactivate. They exist because §72.5 only defined creation, which left an organization's
 * administrator list unreadable and the ownership it establishes permanent.
 *
 * <p>§72.14-§72.15 are ADR-0054: primacy transfer and restore. The delete above became an archive
 * (reversible, visible as archived) rather than a one-way deactivation.
 *
 * <p>§72.17 is ADR-0056: correct the profile. It deliberately does not touch {@code code}, {@code status}
 * or the primary administrator — each of those already has its own endpoint, permission and audit action.
 *
 * <p>§72.16 is ADR-0055: change an organization's status. It finally uses
 * {@code PLATFORM_ORGANIZATION_UPDATE}, which V29 seeded and granted but which no endpoint consumed —
 * an authority the catalogue advertised and the API did not offer.
 */
@RestController
@RequestMapping(path = "/api/v1/platform/organizations", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Platform - Organizations", description = "SUPER_ADMIN business organization lifecycle")
public class PlatformOrganizationController {

    private static final Set<String> SORTABLE = Set.of("code", "name", "status", "createdOn");

    /**
     * Sortable fields for the administrator list. Allow-listed for the same reason as
     * {@link #SORTABLE}: a client-supplied sort field is a column name by another route, and
     * {@code QuerySupport} refuses anything not named here.
     */
    private static final Set<String> ADMIN_SORTABLE = Set.of("email", "firstName", "lastName", "status",
            "createdOn");

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

    @Operation(summary = "Correct a business organization's profile")
    @PatchMapping("/{id}")
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'PLATFORM_ORGANIZATION_UPDATE')")
    public ApiResponse<PlatformDtos.OrganizationDetail> update(@PathVariable UUID id,
            @Valid @RequestBody PlatformDtos.UpdateOrganizationRequest request,
            @AuthenticationPrincipal IdentityPrincipal principal) {
        return ApiResponse.ok("Organization updated",
                organizationService.updateOrganization(id, request, principal.userId()));
    }

    @Operation(summary = "Change a business organization's status (PLATFORM is refused)")
    @PatchMapping("/{id}/status")
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'PLATFORM_ORGANIZATION_UPDATE')")
    public ApiResponse<PlatformDtos.OrganizationDetail> changeStatus(@PathVariable UUID id,
            @Valid @RequestBody PlatformDtos.ChangeOrganizationStatusRequest request,
            @AuthenticationPrincipal IdentityPrincipal principal) {
        return ApiResponse.ok("Organization status updated",
                organizationService.changeStatus(id, request.status(), principal.userId()));
    }

    @Operation(summary = "List an organization's administrators")
    @GetMapping("/{id}/admins")
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'PLATFORM_ADMIN_VIEW')")
    public ApiResponse<java.util.List<PlatformDtos.AdminListEntry>> listAdmins(@PathVariable UUID id,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Status status,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.paged(organizationService.listAdmins(id, search, status,
                QuerySupport.sanitize(pageable, ADMIN_SORTABLE)));
    }

    @Operation(summary = "Replace an administrator's non-administrator roles")
    @PatchMapping("/{id}/admins/{userId}/roles")
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'PLATFORM_ADMIN_ROLE_UPDATE')")
    public ApiResponse<PlatformDtos.AdminListEntry> updateAdminRoles(@PathVariable UUID id,
            @PathVariable UUID userId, @Valid @RequestBody PlatformDtos.UpdateAdminRolesRequest request,
            @AuthenticationPrincipal IdentityPrincipal principal) {
        return ApiResponse.ok("Administrator roles updated",
                organizationService.updateAdminRoles(id, userId, request, principal.userId()));
    }

    @Operation(summary = "Archive an organization administrator (reversible)")
    @DeleteMapping("/{id}/admins/{userId}")
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'PLATFORM_ADMIN_DEACTIVATE')")
    public ApiResponse<Void> archiveAdmin(@PathVariable UUID id, @PathVariable UUID userId,
            @AuthenticationPrincipal IdentityPrincipal principal) {
        organizationService.archiveAdmin(id, userId, principal.userId());
        return ApiResponse.ok("Administrator archived", null);
    }

    @Operation(summary = "Restore an archived organization administrator")
    @PostMapping("/{id}/admins/{userId}/restore")
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'PLATFORM_ADMIN_DEACTIVATE')")
    public ApiResponse<PlatformDtos.AdminListEntry> restoreAdmin(@PathVariable UUID id,
            @PathVariable UUID userId, @AuthenticationPrincipal IdentityPrincipal principal) {
        return ApiResponse.ok("Administrator restored",
                organizationService.restoreAdmin(id, userId, principal.userId()));
    }

    @Operation(summary = "Designate the primary administrator (may change other users' roles)")
    @PatchMapping("/{id}/primary-admin")
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'PLATFORM_ADMIN_PRIMARY_ASSIGN')")
    public ApiResponse<PlatformDtos.OrganizationDetail> setPrimaryAdmin(@PathVariable UUID id,
            @Valid @RequestBody PlatformDtos.SetPrimaryAdminRequest request,
            @AuthenticationPrincipal IdentityPrincipal principal) {
        return ApiResponse.ok("Primary administrator updated",
                organizationService.setPrimaryAdmin(id, request.userId(), principal.userId()));
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
