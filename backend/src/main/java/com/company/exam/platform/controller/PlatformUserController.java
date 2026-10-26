package com.company.exam.platform.controller;

import com.company.exam.common.api.ApiResponse;
import com.company.exam.common.enums.Status;
import com.company.exam.common.web.QuerySupport;
import com.company.exam.platform.dto.PlatformDtos;
import com.company.exam.platform.service.PlatformUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Cross-organization user list ({@code specs/05 §72.11}, ADR-0042).
 *
 * <p>Read-only. There is deliberately no {@code PUT}/{@code DELETE} on this path: platform-tier
 * reach over another organization's users would be the cross-tenant mutation R2 forbids.
 */
@RestController
@RequestMapping(path = "/api/v1/platform/users", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Platform - Users", description = "Cross-organization user list")
public class PlatformUserController {

    private static final Set<String> SORTABLE =
            Set.of("firstName", "lastName", "email", "status", "createdOn");

    private final PlatformUserService userService;

    public PlatformUserController(PlatformUserService userService) {
        this.userService = userService;
    }

    @Operation(summary = "List users across every organization")
    @GetMapping
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'PLATFORM_ADMIN_VIEW')")
    public ApiResponse<java.util.List<PlatformDtos.PlatformUserSummary>> list(
            @RequestParam(required = false) Status status,
            @RequestParam(required = false) UUID organizationId,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.paged(userService.listUsers(status, organizationId, search,
                QuerySupport.sanitize(pageable, SORTABLE)));
    }
}
