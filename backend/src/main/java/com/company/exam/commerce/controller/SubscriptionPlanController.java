package com.company.exam.commerce.controller;

import com.company.exam.commerce.dto.CommerceDtos;
import com.company.exam.commerce.service.CommerceCatalogService;
import com.company.exam.common.api.ApiResponse;
import com.company.exam.common.enums.Status;
import com.company.exam.identity.security.IdentityPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Subscription plan API ({@code specs/18-P9-Commerce-V1.5.md §29.3}). */
@RestController
@RequestMapping(path = "/api/v1/subscription-plans", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Commerce - Subscription plans", description = "Commercial offers and pricing")
public class SubscriptionPlanController {

    private final CommerceCatalogService catalog;

    public SubscriptionPlanController(CommerceCatalogService catalog) {
        this.catalog = catalog;
    }

    @Operation(summary = "List subscription plans")
    @GetMapping
    public ApiResponse<List<CommerceDtos.SubscriptionPlanResponse>> list(
            @RequestParam(required = false) UUID accessPackageId, @RequestParam(required = false) Status status) {
        return ApiResponse.ok(catalog.listPlans(accessPackageId, status));
    }

    @Operation(summary = "Get a subscription plan")
    @GetMapping("/{id}")
    public ApiResponse<CommerceDtos.SubscriptionPlanResponse> get(@PathVariable UUID id) {
        return ApiResponse.ok(catalog.getPlan(id));
    }

    @Operation(summary = "Create a subscription plan")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'SUBSCRIPTION_PLAN_MANAGE')")
    public ApiResponse<CommerceDtos.SubscriptionPlanResponse> create(
            @Valid @RequestBody CommerceDtos.CreateSubscriptionPlanRequest request,
            @AuthenticationPrincipal IdentityPrincipal principal) {
        return ApiResponse.ok("Subscription plan created", catalog.createPlan(request, principal.userId()));
    }

    @Operation(summary = "Update a subscription plan")
    @PutMapping("/{id}")
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'SUBSCRIPTION_PLAN_MANAGE')")
    public ApiResponse<CommerceDtos.SubscriptionPlanResponse> update(@PathVariable UUID id,
            @Valid @RequestBody CommerceDtos.UpdateSubscriptionPlanRequest request,
            @AuthenticationPrincipal IdentityPrincipal principal) {
        return ApiResponse.ok("Subscription plan updated", catalog.updatePlan(id, request, principal.userId()));
    }

    @Operation(summary = "Archive a subscription plan")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'SUBSCRIPTION_PLAN_MANAGE')")
    public void archive(@PathVariable UUID id, @AuthenticationPrincipal IdentityPrincipal principal) {
        catalog.archivePlan(id, principal.userId());
    }
}
