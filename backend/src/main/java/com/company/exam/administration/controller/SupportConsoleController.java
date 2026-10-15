package com.company.exam.administration.controller;

import com.company.exam.administration.dto.AdministrationDtos;
import com.company.exam.administration.service.SupportConsoleService;
import com.company.exam.common.api.ApiResponse;
import com.company.exam.identity.security.IdentityPrincipal;
import com.company.exam.identity.service.AuthorizationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Support console ({@code specs/20-P11-Administration-V1.5.md §34-§36}).
 *
 * <p>Contact data is masked unless the caller holds {@code SUPPORT_USER_CONTACT}; each capability is
 * gated by its own support permission, and support cannot view audit logs, modify roles, refund
 * payments, or publish results ({@code §35}).
 */
@RestController
@RequestMapping(path = "/api/v1/support", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Administration - Support console", description = "Focused operational support view")
public class SupportConsoleController {

    private final SupportConsoleService supportConsoleService;
    private final AuthorizationService authorizationService;

    public SupportConsoleController(SupportConsoleService supportConsoleService,
            AuthorizationService authorizationService) {
        this.supportConsoleService = supportConsoleService;
        this.authorizationService = authorizationService;
    }

    @Operation(summary = "Read a user with default contact masking")
    @GetMapping("/users/{userId}")
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'SUPPORT_USER_VIEW')")
    public ApiResponse<AdministrationDtos.SupportUserView> user(@PathVariable UUID userId,
            Authentication authentication) {
        boolean unmask = authorizationService.hasPermission(authentication, "SUPPORT_USER_CONTACT");
        return ApiResponse.ok(supportConsoleService.userView(userId, unmask));
    }

    @Operation(summary = "Send an ad-hoc notification to one user")
    @PostMapping("/users/{userId}/notifications")
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'SUPPORT_NOTIFICATION_SEND')")
    public ApiResponse<Void> notify(@PathVariable UUID userId,
            @RequestBody SupportNotificationRequest request,
            @AuthenticationPrincipal IdentityPrincipal principal) {
        supportConsoleService.sendNotification(userId, request.title(), request.message(), principal.userId());
        return ApiResponse.ok("Notification sent", null);
    }

    /** A support notification is a single-user, in-app message. */
    public record SupportNotificationRequest(@Size(max = 255) String title, String message) {
    }
}
