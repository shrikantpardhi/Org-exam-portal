package com.company.exam.evaluation.controller;

import com.company.exam.common.api.ApiResponse;
import com.company.exam.evaluation.dto.EvaluationDtos;
import com.company.exam.evaluation.service.AnswerKeyPublicationService;
import com.company.exam.evaluation.service.AnswerKeyService;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Answer key API ({@code specs/15-P6-Evaluation-V1.5.md §88, §140}).
 *
 * <p>Students never receive answer-key mapping; every endpoint requires an explicit evaluation
 * permission ({@code §87, §140}).
 */
@RestController
@RequestMapping(path = "/api/v1/answer-keys", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Evaluation - Answer keys", description = "Versioned answer keys and publication")
public class AnswerKeyController {

    private final AnswerKeyService answerKeyService;
    private final AnswerKeyPublicationService publicationService;

    public AnswerKeyController(AnswerKeyService answerKeyService, AnswerKeyPublicationService publicationService) {
        this.answerKeyService = answerKeyService;
        this.publicationService = publicationService;
    }

    @Operation(summary = "List answer keys")
    @GetMapping
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'ANSWER_KEY_VIEW')")
    public ApiResponse<List<EvaluationDtos.AnswerKeyResponse>> list(
            @RequestParam(required = false) UUID assessmentVersionId) {
        return ApiResponse.ok(answerKeyService.list(assessmentVersionId));
    }

    @Operation(summary = "Create an answer key version")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'ANSWER_KEY_MANAGE')")
    public ApiResponse<EvaluationDtos.AnswerKeyResponse> create(
            @Valid @RequestBody EvaluationDtos.AnswerKeyCreateRequest request,
            @AuthenticationPrincipal IdentityPrincipal principal) {
        return ApiResponse.ok("Answer key created", answerKeyService.create(request, principal.userId()));
    }

    @Operation(summary = "Get an answer key")
    @GetMapping("/{id}")
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'ANSWER_KEY_VIEW')")
    public ApiResponse<EvaluationDtos.AnswerKeyResponse> get(@PathVariable UUID id) {
        return ApiResponse.ok(answerKeyService.get(id));
    }

    @Operation(summary = "Publish an answer key")
    @PostMapping("/{id}/publish")
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'ANSWER_KEY_PUBLISH')")
    public ApiResponse<EvaluationDtos.AnswerKeyResponse> publish(@PathVariable UUID id,
            @AuthenticationPrincipal IdentityPrincipal principal) {
        return ApiResponse.ok("Answer key published", publicationService.publish(id, principal.userId()));
    }
}
