package com.company.exam.administration.controller;

import com.company.exam.administration.dto.AdministrationDtos;
import com.company.exam.administration.service.SystemHealthService;
import com.company.exam.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Aggregated system health ({@code specs/20-P11-Administration-V1.5.md §59}).
 *
 * <p>Returns component status, safe latency, and messages only — never credentials, connection
 * strings, environment dumps, or stack traces ({@code §58}).
 */
@RestController
@RequestMapping(path = "/api/v1/admin/system-health", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Administration - System health", description = "Aggregated operational status")
public class SystemHealthController {

    private final SystemHealthService healthService;

    public SystemHealthController(SystemHealthService healthService) {
        this.healthService = healthService;
    }

    @Operation(summary = "Read aggregated system health")
    @GetMapping
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'SYSTEM_HEALTH_VIEW')")
    public ApiResponse<AdministrationDtos.SystemHealthResponse> health() {
        return ApiResponse.ok(healthService.check());
    }
}
