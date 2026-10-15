package com.company.exam.evaluation.controller;

import com.company.exam.common.api.ApiResponse;
import com.company.exam.evaluation.dto.EvaluationDtos;
import com.company.exam.evaluation.service.ScorecardService;
import com.company.exam.identity.security.IdentityPrincipal;
import com.company.exam.identity.service.AuthorizationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Scorecard API ({@code specs/15-P6-Evaluation-V1.5.md §91, §98, §118, §180}).
 *
 * <p>A scorecard is protected by authenticated user, ownership or staff permission, and secure file
 * access. The raw storage reference is never returned ({@code §58}).
 */
@RestController
@Tag(name = "Evaluation - Scorecards", description = "Scorecard generation and secure access")
public class ScorecardController {

    private final ScorecardService scorecardService;
    private final AuthorizationService authorizationService;

    public ScorecardController(ScorecardService scorecardService, AuthorizationService authorizationService) {
        this.scorecardService = scorecardService;
        this.authorizationService = authorizationService;
    }

    @Operation(summary = "Get the scorecard of a result")
    @GetMapping(path = "/api/v1/results/{resultId}/scorecard", produces = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<EvaluationDtos.ScorecardResponse> get(@PathVariable UUID resultId,
            Authentication authentication, @AuthenticationPrincipal IdentityPrincipal principal) {
        boolean staff = authorizationService.hasPermission(authentication, "SCORECARD_VIEW")
                || authorizationService.hasPermission(authentication, "RESULT_VIEW");
        return ApiResponse.ok("Scorecard retrieved", scorecardService.get(resultId, principal.userId(), staff));
    }

    @Operation(summary = "Generate the scorecard of a result")
    @PostMapping(path = "/api/v1/results/{resultId}/scorecard", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'SCORECARD_GENERATE')")
    public ApiResponse<EvaluationDtos.ScorecardResponse> generate(@PathVariable UUID resultId,
            @AuthenticationPrincipal IdentityPrincipal principal) {
        return ApiResponse.ok("Scorecard generated", scorecardService.generate(resultId, principal.userId()));
    }
}
