package com.company.exam.content.controller;

import com.company.exam.common.api.ApiResponse;
import com.company.exam.content.dto.ContentDtos;
import com.company.exam.content.service.AssetService;
import com.company.exam.identity.security.IdentityPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Asset API ({@code specs/12-P3-Content-V1.5.md §41, §42}). */
@RestController
@RequestMapping(path = "/api/v1/question-versions/{versionId}/assets", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Content - Assets", description = "Version-bound assets")
public class AssetController {

    private final AssetService assetService;

    public AssetController(AssetService assetService) {
        this.assetService = assetService;
    }

    @Operation(summary = "List assets of a version")
    @GetMapping
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'QUESTION_ASSET_VIEW')")
    public ApiResponse<List<ContentDtos.AssetResponse>> list(@PathVariable UUID versionId) {
        return ApiResponse.ok(assetService.list(versionId));
    }

    @Operation(summary = "Upload an asset (multipart)")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'QUESTION_ASSET_CREATE')")
    public ApiResponse<ContentDtos.AssetResponse> upload(@PathVariable UUID versionId,
            @RequestParam("file") MultipartFile file, @AuthenticationPrincipal IdentityPrincipal principal) {
        return ApiResponse.ok("Asset uploaded", assetService.upload(versionId, file, principal.userId()));
    }

    @Operation(summary = "Remove a draft asset")
    @DeleteMapping("/{assetId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'QUESTION_ASSET_REMOVE')")
    public void delete(@PathVariable UUID versionId, @PathVariable UUID assetId,
            @AuthenticationPrincipal IdentityPrincipal principal) {
        assetService.delete(versionId, assetId, principal.userId());
    }
}
