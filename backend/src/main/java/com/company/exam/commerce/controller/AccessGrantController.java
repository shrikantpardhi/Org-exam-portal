package com.company.exam.commerce.controller;

import com.company.exam.commerce.dto.CommerceDtos;
import com.company.exam.commerce.service.AccessGrantService;
import com.company.exam.commerce.service.ReconciliationService;
import com.company.exam.common.api.ApiResponse;
import com.company.exam.common.enums.Status;
import com.company.exam.identity.security.IdentityPrincipal;
import com.company.exam.identity.service.AuthorizationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Access grant API ({@code specs/18-P9-Commerce-V1.5.md §29.7, §30}).
 *
 * <p>Students see their own entitlement; staff/support/finance read according to permission. Manual
 * grants require {@code ACCESS_GRANT_MANAGE} and are audited.
 */
@RestController
@RequestMapping(path = "/api/v1/access-grants", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Commerce - Access grants", description = "The authorization source of truth")
public class AccessGrantController {

    private final AccessGrantService grantService;
    private final ReconciliationService reconciliationService;
    private final AuthorizationService authorizationService;

    public AccessGrantController(AccessGrantService grantService, ReconciliationService reconciliationService,
            AuthorizationService authorizationService) {
        this.grantService = grantService;
        this.reconciliationService = reconciliationService;
        this.authorizationService = authorizationService;
    }

    @Operation(summary = "List access grants")
    @GetMapping
    public ApiResponse<List<CommerceDtos.AccessGrantResponse>> list(@RequestParam(required = false) UUID userId,
            @RequestParam(required = false) UUID accessPackageId, @RequestParam(required = false) Status status,
            @RequestParam(required = false) Boolean active, Authentication authentication,
            @AuthenticationPrincipal IdentityPrincipal principal) {
        boolean staff = authorizationService.hasPermission(authentication, "ACCESS_GRANT_VIEW");
        UUID scopedUser = staff ? userId : principal.userId();
        return ApiResponse.ok(grantService.list(scopedUser, accessPackageId, status, active));
    }

    @Operation(summary = "Get an access grant")
    @GetMapping("/{id}")
    public ApiResponse<CommerceDtos.AccessGrantResponse> get(@PathVariable UUID id, Authentication authentication,
            @AuthenticationPrincipal IdentityPrincipal principal) {
        boolean staff = authorizationService.hasPermission(authentication, "ACCESS_GRANT_VIEW");
        return ApiResponse.ok(grantService.getVisible(id, principal.userId(), staff));
    }

    @Operation(summary = "Create a manual access grant")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'ACCESS_GRANT_MANAGE')")
    public ApiResponse<CommerceDtos.AccessGrantResponse> create(
            @Valid @RequestBody CommerceDtos.CreateAccessGrantRequest request,
            @AuthenticationPrincipal IdentityPrincipal principal) {
        return ApiResponse.ok("Access grant created", grantService.createManual(request, principal.userId()));
    }

    @Operation(summary = "Preview payment reconciliation (finance/admin)")
    @GetMapping("/reconciliation")
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'PURCHASE_VIEW')")
    public ApiResponse<CommerceDtos.ReconciliationReport> reconciliation() {
        return ApiResponse.ok("Reconciliation report", reconciliationService.reconcile());
    }
}
