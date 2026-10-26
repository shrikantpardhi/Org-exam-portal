package com.company.exam.platform.controller;

import com.company.exam.common.api.ApiResponse;
import com.company.exam.identity.security.IdentityPrincipal;
import com.company.exam.platform.dto.PlatformDtos;
import com.company.exam.platform.service.PlatformAdminRecoveryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * SUPER_ADMIN recovery for a locked-out administrator (ADR-0042, D-06).
 *
 * <p>Two endpoints, both organization-scoped so a platform operator cannot reach an administrator
 * by guessing a user id: the organization is part of the path and is checked against the user's own
 * {@code organization_id}.
 */
@RestController
@RequestMapping(path = "/api/v1/platform/organizations/{organizationId}/admins/{userId}",
        produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Platform - Admin recovery", description = "SUPER_ADMIN recovery for a locked-out administrator")
public class PlatformAdminRecoveryController {

    private final PlatformAdminRecoveryService recoveryService;

    public PlatformAdminRecoveryController(PlatformAdminRecoveryService recoveryService) {
        this.recoveryService = recoveryService;
    }

    @Operation(summary = "Issue a password reset for a locked-out administrator")
    @PostMapping("/reset-password")
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'PLATFORM_ADMIN_RECOVER')")
    public ApiResponse<PlatformDtos.AdminRecoveryResponse> resetPassword(
            @PathVariable UUID organizationId, @PathVariable UUID userId,
            @AuthenticationPrincipal IdentityPrincipal principal) {
        return ApiResponse.ok("Password reset issued",
                recoveryService.resetAdminPassword(organizationId, userId, principal.userId()));
    }

    @Operation(summary = "Revoke every active session of an administrator")
    @PostMapping("/unlock-sessions")
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'PLATFORM_ADMIN_RECOVER')")
    public ApiResponse<PlatformDtos.AdminRecoveryResponse> unlockSessions(
            @PathVariable UUID organizationId, @PathVariable UUID userId,
            @AuthenticationPrincipal IdentityPrincipal principal) {
        return ApiResponse.ok("Sessions revoked",
                recoveryService.unlockAdminSessions(organizationId, userId, principal.userId()));
    }
}
