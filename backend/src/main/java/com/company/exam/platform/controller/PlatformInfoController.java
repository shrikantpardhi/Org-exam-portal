package com.company.exam.platform.controller;

import com.company.exam.common.api.ApiResponse;
import com.company.exam.platform.dto.PlatformDtos;
import com.company.exam.platform.service.PlatformInfoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * SUPER_ADMIN platform status and system information ({@code specs/05 §72.6, §72.7}).
 */
@RestController
@RequestMapping(path = "/api/v1/platform", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Platform - Information", description = "SUPER_ADMIN platform status and system information")
public class PlatformInfoController {

    private final PlatformInfoService infoService;

    public PlatformInfoController(PlatformInfoService infoService) {
        this.infoService = infoService;
    }

    @Operation(summary = "Platform status counts and health")
    @GetMapping("/status")
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'PLATFORM_STATUS_VIEW')")
    public ApiResponse<PlatformDtos.PlatformStatusResponse> status() {
        return ApiResponse.ok(infoService.status());
    }

    @Operation(summary = "Platform system information")
    @GetMapping("/system")
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'PLATFORM_SYSTEM_VIEW')")
    public ApiResponse<PlatformDtos.PlatformSystemResponse> system() {
        return ApiResponse.ok(infoService.system());
    }
}
