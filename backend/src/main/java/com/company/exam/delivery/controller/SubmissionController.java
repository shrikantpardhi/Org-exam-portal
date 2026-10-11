package com.company.exam.delivery.controller;

import com.company.exam.common.api.ApiError;
import com.company.exam.common.api.ApiResponse;
import com.company.exam.common.exception.ValidationException;
import com.company.exam.delivery.application.DeliveryDtos;
import com.company.exam.delivery.application.SubmissionService;
import com.company.exam.identity.security.IdentityPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Submission API ({@code specs/14-P5-Delivery-V1.5.md §63}). */
@RestController
@RequestMapping(path = "/api/v1/attempts/{attemptId}/submit", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Delivery - Submission", description = "Idempotent attempt submission")
public class SubmissionController {

    private final SubmissionService submissionService;

    public SubmissionController(SubmissionService submissionService) {
        this.submissionService = submissionService;
    }

    @Operation(summary = "Submit an attempt")
    @PostMapping
    public ApiResponse<DeliveryDtos.SubmitResponse> submit(@PathVariable UUID attemptId,
            @RequestBody(required = false) DeliveryDtos.SubmitRequest request,
            @AuthenticationPrincipal IdentityPrincipal principal) {
        if (request != null && Boolean.FALSE.equals(request.confirmed())) {
            throw new ValidationException("Submission must be confirmed",
                    List.of(ApiError.of("confirmed", "NOT_CONFIRMED", "Submission requires explicit confirmation")));
        }
        return ApiResponse.ok("Assessment submitted successfully",
                submissionService.submit(attemptId, principal.userId()));
    }
}
