package com.company.exam.delivery.controller;

import com.company.exam.common.api.ApiResponse;
import com.company.exam.delivery.application.DeliveryDtos;
import com.company.exam.delivery.application.ResponseSaveService;
import com.company.exam.identity.security.IdentityPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Response autosave API with HTTP conditional (stale-write) protection
 * ({@code specs/14-P5-Delivery-V1.5.md §42-§47}).
 */
@RestController
@RequestMapping(path = "/api/v1/attempts/{attemptId}/responses", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Delivery - Responses", description = "Response autosave with ETag")
public class ResponseController {

    private static final String NO_STORE = "no-store";

    private final ResponseSaveService responseSaveService;

    public ResponseController(ResponseSaveService responseSaveService) {
        this.responseSaveService = responseSaveService;
    }

    @Operation(summary = "Get all responses (recovery)")
    @GetMapping
    public ApiResponse<List<DeliveryDtos.ResponseView>> list(@PathVariable UUID attemptId,
            @AuthenticationPrincipal IdentityPrincipal principal) {
        return ApiResponse.ok("Responses retrieved", responseSaveService.list(attemptId, principal.userId()));
    }

    @Operation(summary = "Get a response with its ETag")
    @GetMapping("/{questionVersionId}")
    public ResponseEntity<ApiResponse<DeliveryDtos.ResponseView>> get(@PathVariable UUID attemptId,
            @PathVariable UUID questionVersionId, @AuthenticationPrincipal IdentityPrincipal principal) {
        DeliveryDtos.ResponseView view = responseSaveService.get(attemptId, questionVersionId, principal.userId());
        return ResponseEntity.ok()
                .header(HttpHeaders.ETAG, quoted(view.etag()))
                .header(HttpHeaders.CACHE_CONTROL, NO_STORE)
                .body(ApiResponse.ok("Response retrieved", view));
    }

    @Operation(summary = "Save a response (optimistic concurrency)")
    @PutMapping("/{questionVersionId}")
    public ResponseEntity<ApiResponse<DeliveryDtos.ResponseView>> save(@PathVariable UUID attemptId,
            @PathVariable UUID questionVersionId,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @RequestBody DeliveryDtos.ResponseSaveRequest request,
            @AuthenticationPrincipal IdentityPrincipal principal) {
        DeliveryDtos.ResponseView view = responseSaveService.save(attemptId, questionVersionId, request, ifMatch,
                principal.userId());
        return ResponseEntity.ok()
                .header(HttpHeaders.ETAG, quoted(view.etag()))
                .body(ApiResponse.ok("Response saved", view));
    }

    private String quoted(String etag) {
        return "\"" + etag + "\"";
    }
}
