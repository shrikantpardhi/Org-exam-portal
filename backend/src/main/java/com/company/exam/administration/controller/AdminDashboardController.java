package com.company.exam.administration.controller;

import com.company.exam.administration.dto.AdministrationDtos;
import com.company.exam.administration.service.AdminDashboardService;
import com.company.exam.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Admin dashboard cards ({@code specs/20-P11-Administration-V1.5.md §21-§23}). */
@RestController
@RequestMapping(path = "/api/v1/admin/dashboard", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Administration - Dashboard", description = "Aggregated administrative cards")
public class AdminDashboardController {

    private final AdminDashboardService dashboardService;

    public AdminDashboardController(AdminDashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @Operation(summary = "Read the admin dashboard")
    @GetMapping
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'ADMIN_DASHBOARD_VIEW')")
    public ApiResponse<AdministrationDtos.AdminDashboardResponse> dashboard() {
        return ApiResponse.ok(dashboardService.dashboard());
    }
}
