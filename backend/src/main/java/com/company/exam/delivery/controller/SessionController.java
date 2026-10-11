package com.company.exam.delivery.controller;

import com.company.exam.common.api.ApiResponse;
import com.company.exam.delivery.application.AssessmentSessionService;
import com.company.exam.delivery.application.DeliveryDtos;
import com.company.exam.identity.security.IdentityPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Session recovery API ({@code specs/14-P5-Delivery-V1.5.md §49-§53}). */
@RestController
@RequestMapping(path = "/api/v1/sessions", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Delivery - Sessions", description = "Runtime session recovery")
public class SessionController {

    private final AssessmentSessionService sessionService;
    private final com.company.exam.identity.service.AuthorizationService authorizationService;

    public SessionController(AssessmentSessionService sessionService,
            com.company.exam.identity.service.AuthorizationService authorizationService) {
        this.sessionService = sessionService;
        this.authorizationService = authorizationService;
    }

    @Operation(summary = "Get the active session")
    @GetMapping("/active")
    public ApiResponse<DeliveryDtos.SessionResponse> active(@AuthenticationPrincipal IdentityPrincipal principal) {
        return ApiResponse.ok("Active session retrieved", sessionService.getActive(principal.userId()));
    }

    @Operation(summary = "Get a session")
    @GetMapping("/{sessionId}")
    public ApiResponse<DeliveryDtos.SessionResponse> get(@PathVariable UUID sessionId,
            @AuthenticationPrincipal IdentityPrincipal principal) {
        return ApiResponse.ok(sessionService.get(sessionId, principal.userId()));
    }

    @Operation(summary = "Recover a session")
    @PostMapping("/{sessionId}/recover")
    public ApiResponse<DeliveryDtos.SessionResponse> recover(@PathVariable UUID sessionId,
            @AuthenticationPrincipal IdentityPrincipal principal) {
        return ApiResponse.ok("Session recovered", sessionService.recover(sessionId, principal.userId()));
    }

    @Operation(summary = "End a session")
    @PostMapping("/{sessionId}/end")
    public ApiResponse<DeliveryDtos.SessionResponse> end(@PathVariable UUID sessionId,
            @AuthenticationPrincipal IdentityPrincipal principal, Authentication authentication) {
        boolean asOperator = authorizationService.hasPermission(authentication, "DELIVERY_OPERATE");
        return ApiResponse.ok("Session ended", sessionService.end(sessionId, principal.userId(), asOperator));
    }
}
