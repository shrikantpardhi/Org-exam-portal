package com.company.exam.reporting.controller;

import com.company.exam.common.api.ApiResponse;
import com.company.exam.common.exception.AuthorizationException;
import com.company.exam.identity.security.IdentityPrincipal;
import com.company.exam.identity.service.AuthorizationService;
import com.company.exam.reporting.catalog.ReportType;
import com.company.exam.reporting.dto.ReportingDtos;
import com.company.exam.reporting.service.ReportService;
import com.company.exam.reporting.validation.ReportRequestValidator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
 * Reporting API ({@code specs/22-P13-Reporting-V1.5.md §14, §22, §28, §29}).
 *
 * <p>The request body accepts a report type, filters, and a format — never SQL, a table name, or a
 * column expression, because only registered report types map to controlled query implementations
 * ({@code §106}). Download is authorized independently of generation ({@code §22}).
 */
@RestController
@RequestMapping(path = "/api/v1/reports", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Reporting", description = "Operational and executive reporting")
public class ReportController {

    /** Operational read grants cross-user report visibility; otherwise a caller sees only their own. */
    private static final String CROSS_USER_PERMISSION = "REPORT_OPERATIONS_READ";

    private final ReportService reportService;
    private final ReportRequestValidator validator;
    private final AuthorizationService authorizationService;

    public ReportController(ReportService reportService, ReportRequestValidator validator,
            AuthorizationService authorizationService) {
        this.reportService = reportService;
        this.validator = validator;
        this.authorizationService = authorizationService;
    }

    @Operation(summary = "List the report catalog")
    @GetMapping("/catalog")
    public ApiResponse<List<ReportingDtos.ReportCatalogEntry>> catalog(Authentication authentication) {
        // Only report types the caller may actually request are advertised.
        List<ReportingDtos.ReportCatalogEntry> entries = reportService.catalogEntries().stream()
                .filter(entry -> authorizationService.hasPermission(authentication, entry.permission()))
                .toList();
        return ApiResponse.ok(entries);
    }

    @Operation(summary = "Request a report (synchronous when small, queued when large)")
    @PostMapping
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<?> request(@Valid @RequestBody ReportingDtos.ReportRequest request,
            Authentication authentication, @AuthenticationPrincipal IdentityPrincipal principal) {
        // The required permission is a property of the report type, so it is resolved then enforced.
        ReportType type = validator.resolveType(request.reportType());
        if (!authorizationService.hasPermission(authentication, type.permission())) {
            throw new AuthorizationException("You are not permitted to request that report");
        }
        ReportService.ReportExecution execution = reportService.request(request, principal.userId(),
                isStudent(authentication));
        if (execution.accepted() != null) {
            return ApiResponse.ok("Report generation started", execution.accepted());
        }
        return ApiResponse.ok("Report generated", execution.result());
    }

    @Operation(summary = "Read a report job's status")
    @GetMapping("/jobs/{jobId}")
    public ApiResponse<ReportingDtos.ReportJobStatus> status(@PathVariable UUID jobId, Authentication authentication,
            @AuthenticationPrincipal IdentityPrincipal principal) {
        return ApiResponse.ok(reportService.status(jobId, principal.userId(), canReadAny(authentication)));
    }

    @Operation(summary = "Download a generated report artifact")
    @GetMapping("/jobs/{jobId}/download")
    public ResponseEntity<byte[]> download(@PathVariable UUID jobId,
            @RequestParam(required = false) String token, Authentication authentication,
            @AuthenticationPrincipal IdentityPrincipal principal) {
        ReportService.Artifact artifact = reportService.read(jobId, principal.userId(),
                canReadAny(authentication), token);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, artifact.contentType())
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + artifact.fileName() + "\"")
                // A report artifact is never cached by an intermediary.
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(artifact.content());
    }

    @Operation(summary = "List my report history")
    @GetMapping("/history")
    public ApiResponse<List<ReportingDtos.ReportHistoryEntry>> history(
            @RequestParam(defaultValue = "50") int limit, Authentication authentication,
            @AuthenticationPrincipal IdentityPrincipal principal) {
        return ApiResponse.ok(reportService.history(principal.userId(), canReadAny(authentication),
                Math.max(1, Math.min(limit, 200))));
    }

    private boolean canReadAny(Authentication authentication) {
        return authorizationService.hasPermission(authentication, CROSS_USER_PERMISSION);
    }

    /**
     * A caller who is neither an operations reader nor an executive reader is treated as a student and
     * has every report automatically scoped to their own identity ({@code §76}).
     */
    private boolean isStudent(Authentication authentication) {
        return !authorizationService.hasPermission(authentication, CROSS_USER_PERMISSION)
                && !authorizationService.hasPermission(authentication, "REPORT_EXECUTIVE_READ");
    }
}
