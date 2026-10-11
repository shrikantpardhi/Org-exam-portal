package com.company.exam.identity.controller;

import com.company.exam.common.api.ApiResponse;
import com.company.exam.identity.dto.CurrentUserDtos;
import com.company.exam.identity.security.IdentityPrincipal;
import com.company.exam.identity.service.AuthorizationService;
import com.company.exam.identity.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Current-user self-service API ({@code specs/10-P1-Identity-V1.5.md §32, §33, §67}).
 *
 * <p>These endpoints are self-context and require only authentication; they do not grant the
 * broader administrative identity permissions.
 */
@RestController
@RequestMapping(path = "/api/v1/me", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Current user", description = "Authenticated user's own context and permissions")
public class CurrentUserController {

    private final UserService userService;
    private final AuthorizationService authorizationService;

    public CurrentUserController(UserService userService, AuthorizationService authorizationService) {
        this.userService = userService;
        this.authorizationService = authorizationService;
    }

    @Operation(summary = "Current user", description = "Returns the authenticated user's safe profile context.")
    @GetMapping
    public ApiResponse<CurrentUserDtos.CurrentUserResponse> me(
            @AuthenticationPrincipal IdentityPrincipal principal) {
        return ApiResponse.ok("Current user retrieved", userService.currentUser(principal.userId()));
    }

    @Operation(summary = "Current permissions",
            description = "Returns the caller's effective permission codes for UI behaviour.")
    @GetMapping("/permissions")
    public ApiResponse<CurrentUserDtos.CurrentPermissionsResponse> permissions(
            @AuthenticationPrincipal IdentityPrincipal principal) {
        return ApiResponse.ok("Permissions retrieved successfully",
                new CurrentUserDtos.CurrentPermissionsResponse(
                        authorizationService.effectivePermissions(principal.userId())));
    }
}
