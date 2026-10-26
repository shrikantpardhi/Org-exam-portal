package com.company.exam.administration.controller;

import com.company.exam.administration.dto.AdministrationDtos;
import com.company.exam.administration.service.AdminOrganizationConfigService;
import com.company.exam.common.api.ApiResponse;
import com.company.exam.identity.security.IdentityPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * ADMIN organization-scoped configuration ({@code specs/05 §73.1}; ADR-0037).
 *
 * <p>Every operation is scoped to the caller's own organization, taken from the authenticated principal
 * rather than from the request. Reading is gated on the ADMIN role; writing additionally requires
 * {@code ORGANIZATION_SETTING_UPDATE}, so a read-only administrator can view the profile but not change
 * it.
 */
@RestController
@RequestMapping(path = "/api/v1/admin/organization-config", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Administration - Organization configuration", description = "Caller's organization-scoped configuration")
public class AdminOrganizationConfigController {

    private final AdminOrganizationConfigService configService;

    public AdminOrganizationConfigController(AdminOrganizationConfigService configService) {
        this.configService = configService;
    }

    @Operation(summary = "Read the caller's organization-scoped configuration")
    @GetMapping
    @PreAuthorize("@authorizationService.hasRole(authentication, 'ADMIN')")
    public ApiResponse<AdministrationDtos.OrganizationConfigResponse> get(
            @AuthenticationPrincipal IdentityPrincipal principal) {
        return ApiResponse.ok(configService.get(principal.userId()));
    }

    /**
     * Updates the caller's organization profile (V37, ADR-0051).
     *
     * <p>The organization is resolved from the principal, never from a path variable, so there is no
     * id an administrator could tamper with to reach another tenant.
     */
    @Operation(summary = "Update the caller's organization profile")
    @PutMapping(path = "/profile", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'ORGANIZATION_SETTING_UPDATE')")
    public ApiResponse<AdministrationDtos.OrganizationConfigOrganization> updateProfile(
            @AuthenticationPrincipal IdentityPrincipal principal,
            @Valid @RequestBody AdministrationDtos.UpdateOrganizationProfileRequest request) {
        return ApiResponse.ok(configService.updateProfile(request, principal.userId()));
    }
}
