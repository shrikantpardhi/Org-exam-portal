package com.company.exam.administration.controller;

import com.company.exam.administration.dto.AdministrationDtos;
import com.company.exam.administration.service.BusinessRuleService;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Business rule API ({@code specs/20-P11-Administration-V1.5.md §9.5}).
 *
 * <p>DELETE is not frozen; lifecycle state is used instead of destructive deletion ({@code §9.5, §9.6}).
 */
@RestController
@RequestMapping(path = "/api/v1/business-rules", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Administration - Business rules", description = "Configurable business behaviour")
public class BusinessRuleController {

    private final BusinessRuleService ruleService;

    public BusinessRuleController(BusinessRuleService ruleService) {
        this.ruleService = ruleService;
    }

    @Operation(summary = "List business rules")
    @GetMapping
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'BUSINESS_RULE_VIEW')")
    public ApiResponse<List<AdministrationDtos.BusinessRuleResponse>> list(
            @RequestParam(required = false) Status status) {
        return ApiResponse.ok(ruleService.list(status));
    }

    @Operation(summary = "Get a business rule")
    @GetMapping("/{id}")
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'BUSINESS_RULE_VIEW')")
    public ApiResponse<AdministrationDtos.BusinessRuleResponse> get(@PathVariable UUID id) {
        return ApiResponse.ok(ruleService.get(id));
    }

    @Operation(summary = "Create a business rule")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'BUSINESS_RULE_CREATE')")
    public ApiResponse<AdministrationDtos.BusinessRuleResponse> create(
            @Valid @RequestBody AdministrationDtos.CreateBusinessRuleRequest request,
            @AuthenticationPrincipal IdentityPrincipal principal) {
        return ApiResponse.ok("Business rule created", ruleService.create(request, principal.userId()));
    }

    @Operation(summary = "Update a business rule")
    @PutMapping("/{id}")
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'BUSINESS_RULE_UPDATE')")
    public ApiResponse<AdministrationDtos.BusinessRuleResponse> update(@PathVariable UUID id,
            @Valid @RequestBody AdministrationDtos.UpdateBusinessRuleRequest request,
            @AuthenticationPrincipal IdentityPrincipal principal) {
        return ApiResponse.ok("Business rule updated", ruleService.update(id, request, principal.userId()));
    }
}
