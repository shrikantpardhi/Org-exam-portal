package com.company.exam.evaluation.controller;

import com.company.exam.common.api.ApiResponse;
import com.company.exam.common.api.PageResponse;
import com.company.exam.common.web.QuerySupport;
import com.company.exam.evaluation.dto.EvaluationDtos;
import com.company.exam.evaluation.service.ResultPublicationService;
import com.company.exam.evaluation.service.ResultService;
import com.company.exam.identity.security.IdentityPrincipal;
import com.company.exam.identity.service.AuthorizationService;
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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Result API ({@code specs/15-P6-Evaluation-V1.5.md §90, §95-§99, §142}).
 *
 * <p>Students read only their own PUBLISHED result; the staff listing and rankings require
 * {@code RESULT_VIEW}. Students receive masked rankings entries ({@code §39, §99, §142}).
 */
@RestController
@Tag(name = "Evaluation - Results", description = "Results, publication, and rankings")
public class ResultController {

    private static final Set<String> SORTABLE = Set.of("rank", "obtainedMarks", "percentage", "resultStatus",
            "createdOn", "updatedOn");

    private final ResultService resultService;
    private final ResultPublicationService publicationService;
    private final AuthorizationService authorizationService;

    public ResultController(ResultService resultService, ResultPublicationService publicationService,
            AuthorizationService authorizationService) {
        this.resultService = resultService;
        this.publicationService = publicationService;
        this.authorizationService = authorizationService;
    }

    @Operation(summary = "List results")
    @GetMapping(path = "/api/v1/results", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'RESULT_VIEW')")
    public ApiResponse<java.util.List<EvaluationDtos.ResultResponse>> list(
            @RequestParam(required = false) UUID assessmentId,
            @RequestParam(required = false) UUID assessmentVersionId,
            @RequestParam(required = false) UUID studentId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @PageableDefault(size = 20) Pageable pageable) {
        PageResponse<EvaluationDtos.ResultResponse> page = resultService.list(assessmentId, assessmentVersionId,
                studentId, status, from, to, QuerySupport.sanitize(pageable, SORTABLE));
        return ApiResponse.paged(page);
    }

    @Operation(summary = "Get a result")
    @GetMapping(path = "/api/v1/results/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<EvaluationDtos.ResultResponse> get(@PathVariable UUID id, Authentication authentication,
            @AuthenticationPrincipal IdentityPrincipal principal) {
        boolean staff = authorizationService.hasPermission(authentication, "RESULT_VIEW");
        return ApiResponse.ok(resultService.getVisible(id, principal.userId(), staff));
    }

    @Operation(summary = "Get the current result of an attempt")
    @GetMapping(path = "/api/v1/attempts/{attemptId}/result", produces = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<EvaluationDtos.ResultResponse> attemptResult(@PathVariable UUID attemptId,
            Authentication authentication, @AuthenticationPrincipal IdentityPrincipal principal) {
        boolean staff = authorizationService.hasPermission(authentication, "RESULT_VIEW");
        return ApiResponse.ok(resultService.getForAttempt(attemptId, principal.userId(), staff));
    }

    @Operation(summary = "Publish a result")
    @PostMapping(path = "/api/v1/results/{resultId}/publish", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'RESULT_PUBLISH')")
    public ApiResponse<EvaluationDtos.ResultResponse> publish(@PathVariable UUID resultId,
            @AuthenticationPrincipal IdentityPrincipal principal) {
        return ApiResponse.ok("Result published", publicationService.publish(resultId, principal.userId()));
    }

    @Operation(summary = "Rankings for an assessment")
    @GetMapping(path = "/api/v1/assessments/{assessmentId}/rankings", produces = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<java.util.List<EvaluationDtos.RankingsEntry>> rankings(@PathVariable UUID assessmentId,
            @RequestParam(required = false) UUID assessmentVersionId,
            @RequestParam(required = false) UUID scheduleId,
            @PageableDefault(size = 50) Pageable pageable, Authentication authentication) {
        boolean staff = authorizationService.hasPermission(authentication, "RESULT_VIEW");
        PageResponse<EvaluationDtos.RankingsEntry> page = resultService.rankings(assessmentId, assessmentVersionId,
                scheduleId, staff, QuerySupport.sanitize(pageable, SORTABLE));
        return ApiResponse.paged("Rankings retrieved", page);
    }
}
