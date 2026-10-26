package com.company.exam.administration.controller;

import com.company.exam.administration.dto.AdministrationDtos;
import com.company.exam.administration.service.AuditQueryService;
import com.company.exam.common.api.ApiResponse;
import com.company.exam.common.api.PageResponse;
import com.company.exam.common.web.QuerySupport;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The audit explorer ({@code specs/20-P11-Administration-V1.5.md §11.8, §11.9}).
 *
 * <p>Read-only: no update or delete endpoint is exposed, and students receive no audit access
 * ({@code §11.2, §11.9}). Access requires the explicit {@code AUDIT_LOG_VIEW} permission; ADMIN does
 * not bypass it ({@code §6}).
 */
@RestController
@RequestMapping(path = "/api/v1/audit-logs", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Administration - Audit", description = "The immutable audit trail")
@PreAuthorize("@authorizationService.hasPermission(authentication, 'AUDIT_LOG_VIEW')")
public class AuditLogController {

    private static final Set<String> SORTABLE = Set.of("createdOn", "action", "entityName");

    private final AuditQueryService auditQueryService;

    public AuditLogController(AuditQueryService auditQueryService) {
        this.auditQueryService = auditQueryService;
    }

    @Operation(summary = "Search the audit trail")
    @GetMapping
    public ApiResponse<java.util.List<AdministrationDtos.AuditLogResponse>> search(
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) String entityName,
            @RequestParam(required = false) UUID entityId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @PageableDefault(size = 20) Pageable pageable,
            @org.springframework.security.core.annotation.AuthenticationPrincipal
            com.company.exam.identity.security.IdentityPrincipal principal) {
        PageResponse<AdministrationDtos.AuditLogResponse> page = PageResponse.from(
                auditQueryService.search(userId, entityName, entityId, action, from, to,
                        QuerySupport.sanitize(pageable, SORTABLE), principal.userId()),
                entry -> entry);
        return ApiResponse.paged("Audit trail retrieved", page);
    }
}
