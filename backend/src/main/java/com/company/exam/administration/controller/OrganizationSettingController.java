package com.company.exam.administration.controller;

import com.company.exam.administration.dto.AdministrationDtos;
import com.company.exam.administration.service.OrganizationSettingService;
import com.company.exam.common.api.ApiResponse;
import com.company.exam.identity.api.AnalyticsIdentityCatalog;
import com.company.exam.identity.security.IdentityPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Organization settings API ({@code specs/20-P11-Administration-V1.5.md §8.5}).
 *
 * <p>Scoped to the current deployment organization: a normal request never selects an arbitrary
 * organization by query parameter.
 */
@RestController
@RequestMapping(path = "/api/v1/organization-settings", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Administration - Organization settings", description = "Organization configuration")
public class OrganizationSettingController {

    private final OrganizationSettingService settingService;
    private final AnalyticsIdentityCatalog identityCatalog;

    public OrganizationSettingController(OrganizationSettingService settingService,
            AnalyticsIdentityCatalog identityCatalog) {
        this.settingService = settingService;
        this.identityCatalog = identityCatalog;
    }

    @Operation(summary = "Read the organization configuration")
    @GetMapping
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'ORGANIZATION_SETTING_VIEW')")
    public ApiResponse<AdministrationDtos.OrganizationSettingResponse> get() {
        return ApiResponse.ok(settingService.get(deploymentOrganizationId()));
    }

    @Operation(summary = "Update the organization configuration")
    @PutMapping
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'ORGANIZATION_SETTING_UPDATE')")
    public ApiResponse<AdministrationDtos.OrganizationSettingResponse> update(
            @RequestBody AdministrationDtos.UpdateOrganizationSettingRequest request,
            @AuthenticationPrincipal IdentityPrincipal principal) {
        return ApiResponse.ok("Organization configuration updated",
                settingService.update(deploymentOrganizationId(), request.configuration(), principal.userId()));
    }

    /** The single organization of this deployment; a client cannot choose it. */
    private UUID deploymentOrganizationId() {
        List<UUID> organizations = identityCatalog.organizationIds();
        return organizations.isEmpty() ? null : organizations.get(0);
    }
}
