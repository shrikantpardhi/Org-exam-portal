package com.company.exam.evaluation.controller;

import com.company.exam.common.api.ApiResponse;
import com.company.exam.evaluation.dto.EvaluationDtos;
import com.company.exam.evaluation.service.AnswerKeyDetailService;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Answer key detail API ({@code specs/15-P6-Evaluation-V1.5.md §30, §48, §88}). */
@RestController
@RequestMapping(path = "/api/v1/answer-keys/{answerKeyId}/details", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Evaluation - Answer key details", description = "Authoritative correctness records")
public class AnswerKeyDetailController {

    private final AnswerKeyDetailService detailService;

    public AnswerKeyDetailController(AnswerKeyDetailService detailService) {
        this.detailService = detailService;
    }

    @Operation(summary = "List answer key details")
    @GetMapping
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'ANSWER_KEY_VIEW')")
    public ApiResponse<List<EvaluationDtos.AnswerKeyDetailResponse>> list(@PathVariable UUID answerKeyId) {
        return ApiResponse.ok(detailService.list(answerKeyId));
    }

    @Operation(summary = "Add an answer key detail")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'ANSWER_KEY_MANAGE')")
    public ApiResponse<EvaluationDtos.AnswerKeyDetailResponse> create(@PathVariable UUID answerKeyId,
            @Valid @RequestBody EvaluationDtos.AnswerKeyDetailRequest request,
            @AuthenticationPrincipal IdentityPrincipal principal) {
        return ApiResponse.ok("Answer key detail created",
                detailService.create(answerKeyId, request, principal.userId()));
    }

    @Operation(summary = "Update an answer key detail")
    @PutMapping("/{detailId}")
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'ANSWER_KEY_MANAGE')")
    public ApiResponse<EvaluationDtos.AnswerKeyDetailResponse> update(@PathVariable UUID answerKeyId,
            @PathVariable UUID detailId, @Valid @RequestBody EvaluationDtos.AnswerKeyDetailRequest request,
            @AuthenticationPrincipal IdentityPrincipal principal) {
        return ApiResponse.ok("Answer key detail updated",
                detailService.update(answerKeyId, detailId, request, principal.userId()));
    }

    @Operation(summary = "Delete an answer key detail")
    @DeleteMapping("/{detailId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'ANSWER_KEY_MANAGE')")
    public void delete(@PathVariable UUID answerKeyId, @PathVariable UUID detailId,
            @AuthenticationPrincipal IdentityPrincipal principal) {
        detailService.delete(answerKeyId, detailId, principal.userId());
    }
}
