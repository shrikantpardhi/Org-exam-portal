package com.company.exam.delivery.controller;

import com.company.exam.common.api.ApiResponse;
import com.company.exam.common.api.PageResponse;
import com.company.exam.delivery.application.AttemptQueryService;
import com.company.exam.delivery.application.DeliveryDtos;
import com.company.exam.identity.security.IdentityPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Attempt read API ({@code specs/14-P5-Delivery-V1.5.md §38-§41, §69}). */
@RestController
@RequestMapping(path = "/api/v1/attempts", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Delivery - Attempts", description = "Runtime attempt state and prepared snapshot")
public class AttemptController {

    private static final Set<String> SORTABLE = Set.of("startedOn", "submittedOn", "status");

    private final AttemptQueryService attemptQueryService;

    public AttemptController(AttemptQueryService attemptQueryService) {
        this.attemptQueryService = attemptQueryService;
    }

    @Operation(summary = "List attempts")
    @GetMapping
    public ApiResponse<List<DeliveryDtos.AttemptResponse>> list(
            @RequestParam(required = false) UUID assessmentVersionId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @PageableDefault(size = 20) Pageable pageable,
            @AuthenticationPrincipal IdentityPrincipal principal) {
        List<DeliveryDtos.AttemptResponse> attempts = attemptQueryService.list(principal.userId(),
                assessmentVersionId, status, from, to);
        return ApiResponse.paged(PageResponse.from(new PageImpl<>(attempts, pageable, attempts.size()),
                item -> item));
    }

    @Operation(summary = "Get an attempt")
    @GetMapping("/{attemptId}")
    public ApiResponse<DeliveryDtos.AttemptResponse> get(@PathVariable UUID attemptId,
            @AuthenticationPrincipal IdentityPrincipal principal) {
        return ApiResponse.ok("Attempt retrieved", attemptQueryService.getAttempt(attemptId, principal.userId()));
    }

    @Operation(summary = "Get the prepared attempt snapshot")
    @GetMapping("/{attemptId}/questions")
    public ApiResponse<List<DeliveryDtos.AttemptQuestionView>> questions(@PathVariable UUID attemptId,
            @AuthenticationPrincipal IdentityPrincipal principal) {
        return ApiResponse.ok("Attempt questions retrieved",
                attemptQueryService.questions(attemptId, principal.userId()));
    }

    @Operation(summary = "Get one attempt question")
    @GetMapping("/{attemptId}/questions/{questionVersionId}")
    public ApiResponse<DeliveryDtos.AttemptQuestionView> question(@PathVariable UUID attemptId,
            @PathVariable UUID questionVersionId, @AuthenticationPrincipal IdentityPrincipal principal) {
        return ApiResponse.ok("Attempt question retrieved",
                attemptQueryService.question(attemptId, questionVersionId, principal.userId()));
    }
}
