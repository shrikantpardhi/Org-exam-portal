package com.company.exam.delivery.controller;

import com.company.exam.common.api.ApiResponse;
import com.company.exam.common.api.PageResponse;
import com.company.exam.delivery.application.AttemptQueryService;
import com.company.exam.delivery.application.DeliveryDtos;
import com.company.exam.identity.security.IdentityPrincipal;
import com.company.exam.identity.service.AuthorizationService;
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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Attempt read API ({@code specs/14-P5-Delivery-V1.5.md §38-§41, §69, §83}). */
@RestController
@RequestMapping(path = "/api/v1/attempts", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Delivery - Attempts", description = "Runtime attempt state and prepared snapshot")
public class AttemptController {

    /**
     * The permission that widens the attempt read beyond the caller's own attempts
     * ({@code specs/14-P5-Delivery-V1.5.md §83}). It is seeded as a Delivery operational code
     * (GAP-056) and is resolved from the authenticated principal, never from the request.
     */
    static final String VIEW_ANY_PERMISSION = "DELIVERY_ATTEMPT_VIEW_ANY";

    private static final Set<String> SORTABLE = Set.of("startedOn", "submittedOn", "status");

    private final AttemptQueryService attemptQueryService;
    private final AuthorizationService authorizationService;

    public AttemptController(AttemptQueryService attemptQueryService,
            AuthorizationService authorizationService) {
        this.attemptQueryService = attemptQueryService;
        this.authorizationService = authorizationService;
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
            Authentication authentication, @AuthenticationPrincipal IdentityPrincipal principal) {
        // Self-scoping is the default. The staff scope is a widening of the same read, granted only
        // by the DELIVERY_ATTEMPT_VIEW_ANY permission resolved from the authenticated principal
        // (specs/14-P5-Delivery-V1.5.md §83). It is never taken from the request.
        boolean canViewAny = authorizationService.hasPermission(authentication, VIEW_ANY_PERMISSION);
        return ApiResponse.ok("Attempt retrieved",
                attemptQueryService.getAttempt(attemptId, principal.userId(), canViewAny));
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
