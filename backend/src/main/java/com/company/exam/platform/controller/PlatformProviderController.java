package com.company.exam.platform.controller;

import com.company.exam.common.api.ApiResponse;
import com.company.exam.identity.security.IdentityPrincipal;
import com.company.exam.platform.dto.PlatformDtos;
import com.company.exam.platform.service.PlatformProviderService;
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
 * SUPER_ADMIN provider configuration ({@code specs/05 §72.8, §72.9}).
 */
@RestController
@RequestMapping(path = "/api/v1/platform/providers", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Platform - Providers", description = "SUPER_ADMIN provider configuration")
public class PlatformProviderController {

    private final PlatformProviderService providerService;

    public PlatformProviderController(PlatformProviderService providerService) {
        this.providerService = providerService;
    }

    @Operation(summary = "Read masked provider configuration")
    @GetMapping
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'PLATFORM_PROVIDER_VIEW')")
    public ApiResponse<PlatformDtos.ProvidersResponse> get() {
        return ApiResponse.ok(providerService.get());
    }

    @Operation(summary = "Update provider configuration")
    @PutMapping
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'PLATFORM_PROVIDER_UPDATE')")
    public ApiResponse<PlatformDtos.ProvidersResponse> update(
            @Valid @RequestBody PlatformDtos.ProviderConfigUpdate request,
            @AuthenticationPrincipal IdentityPrincipal principal) {
        return ApiResponse.ok("Provider configuration updated",
                providerService.update(request, principal.userId()));
    }
}
