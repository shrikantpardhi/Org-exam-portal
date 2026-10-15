package com.company.exam.evaluation.controller;

import com.company.exam.common.api.ApiResponse;
import com.company.exam.evaluation.dto.EvaluationDtos;
import com.company.exam.evaluation.service.EvaluationService;
import com.company.exam.evaluation.service.ReEvaluationService;
import com.company.exam.identity.security.IdentityPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Evaluation API ({@code specs/15-P6-Evaluation-V1.5.md §89, §92, §93, §94, §141}).
 *
 * <p>Evaluation and re-evaluation are privileged operations; evaluation internals are restricted
 * ({@code §94, §141}).
 */
@RestController
@Tag(name = "Evaluation - Evaluations", description = "Evaluate and re-evaluate submitted attempts")
public class EvaluationController {

    private final EvaluationService evaluationService;
    private final ReEvaluationService reEvaluationService;

    public EvaluationController(EvaluationService evaluationService, ReEvaluationService reEvaluationService) {
        this.evaluationService = evaluationService;
        this.reEvaluationService = reEvaluationService;
    }

    @Operation(summary = "Evaluate a submitted attempt")
    @PostMapping(path = "/api/v1/attempts/{attemptId}/evaluate", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'EVALUATION_RUN')")
    public ApiResponse<EvaluationDtos.EvaluationResponse> evaluate(@PathVariable UUID attemptId,
            @Valid @RequestBody EvaluationDtos.EvaluateRequest request,
            @AuthenticationPrincipal IdentityPrincipal principal) {
        return ApiResponse.ok("Evaluation completed",
                evaluationService.evaluate(attemptId, request.answerKeyId(), principal.userId()));
    }

    @Operation(summary = "Re-evaluate a submitted attempt")
    @PostMapping(path = "/api/v1/attempts/{attemptId}/re-evaluate", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'EVALUATION_RUN')")
    public ApiResponse<EvaluationDtos.ReEvaluationResponse> reEvaluate(@PathVariable UUID attemptId,
            @Valid @RequestBody EvaluationDtos.ReEvaluateRequest request,
            @AuthenticationPrincipal IdentityPrincipal principal) {
        return ApiResponse.ok("Re-evaluation completed", reEvaluationService.reEvaluate(attemptId,
                request.answerKeyId(), request.reason(), principal.userId()));
    }

    @Operation(summary = "List the evaluation history of an attempt")
    @GetMapping(path = "/api/v1/attempts/{attemptId}/evaluations", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'EVALUATION_VIEW_HISTORY')")
    public ApiResponse<List<EvaluationDtos.EvaluationResponse>> history(@PathVariable UUID attemptId) {
        return ApiResponse.ok(evaluationService.history(attemptId));
    }

    @Operation(summary = "Get an evaluation")
    @GetMapping(path = "/api/v1/evaluations/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'EVALUATION_VIEW')")
    public ApiResponse<EvaluationDtos.EvaluationResponse> get(@PathVariable UUID id) {
        return ApiResponse.ok(evaluationService.get(id));
    }
}
