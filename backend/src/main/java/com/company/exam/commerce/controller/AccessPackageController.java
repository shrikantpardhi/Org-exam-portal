package com.company.exam.commerce.controller;

import com.company.exam.commerce.dto.CommerceDtos;
import com.company.exam.commerce.service.CommerceCatalogService;
import com.company.exam.common.api.ApiResponse;
import com.company.exam.common.enums.Status;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Access package API and package-to-collection mapping
 * ({@code specs/18-P9-Commerce-V1.5.md §29.1, §29.2, §30}).
 *
 * <p>Browsing the catalog is available to any authenticated user; management is permission-gated.
 */
@RestController
@RequestMapping(path = "/api/v1/access-packages", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Commerce - Access packages", description = "Entitlement bundles and collection mapping")
public class AccessPackageController {

    private final CommerceCatalogService catalog;

    public AccessPackageController(CommerceCatalogService catalog) {
        this.catalog = catalog;
    }

    @Operation(summary = "List access packages")
    @GetMapping
    public ApiResponse<List<CommerceDtos.AccessPackageResponse>> list(@RequestParam(required = false) Status status) {
        return ApiResponse.ok(catalog.listPackages(status));
    }

    @Operation(summary = "Get an access package")
    @GetMapping("/{id}")
    public ApiResponse<CommerceDtos.AccessPackageResponse> get(@PathVariable UUID id) {
        return ApiResponse.ok(catalog.getPackage(id));
    }

    @Operation(summary = "Create an access package")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'ACCESS_PACKAGE_MANAGE')")
    public ApiResponse<CommerceDtos.AccessPackageResponse> create(
            @Valid @RequestBody CommerceDtos.CreateAccessPackageRequest request,
            @AuthenticationPrincipal IdentityPrincipal principal) {
        return ApiResponse.ok("Access package created", catalog.createPackage(request, principal.userId()));
    }

    @Operation(summary = "Update an access package")
    @PutMapping("/{id}")
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'ACCESS_PACKAGE_MANAGE')")
    public ApiResponse<CommerceDtos.AccessPackageResponse> update(@PathVariable UUID id,
            @Valid @RequestBody CommerceDtos.UpdateAccessPackageRequest request,
            @AuthenticationPrincipal IdentityPrincipal principal) {
        return ApiResponse.ok("Access package updated", catalog.updatePackage(id, request, principal.userId()));
    }

    @Operation(summary = "Archive an access package")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'ACCESS_PACKAGE_MANAGE')")
    public void archive(@PathVariable UUID id, @AuthenticationPrincipal IdentityPrincipal principal) {
        catalog.archivePackage(id, principal.userId());
    }

    @Operation(summary = "List the collections in a package")
    @GetMapping("/{packageId}/collections")
    public ApiResponse<List<CommerceDtos.PackageCollectionResponse>> collections(@PathVariable UUID packageId) {
        return ApiResponse.ok(catalog.listPackageCollections(packageId));
    }

    @Operation(summary = "Map a collection to a package")
    @PostMapping("/{packageId}/collections")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'ACCESS_PACKAGE_MANAGE')")
    public ApiResponse<CommerceDtos.PackageCollectionResponse> addCollection(@PathVariable UUID packageId,
            @Valid @RequestBody CommerceDtos.AddPackageCollectionRequest request,
            @AuthenticationPrincipal IdentityPrincipal principal) {
        return ApiResponse.ok("Collection mapped",
                catalog.addPackageCollection(packageId, request.collectionId(), principal.userId()));
    }

    @Operation(summary = "Remove a collection from a package")
    @DeleteMapping("/{packageId}/collections/{collectionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'ACCESS_PACKAGE_MANAGE')")
    public void removeCollection(@PathVariable UUID packageId, @PathVariable UUID collectionId,
            @AuthenticationPrincipal IdentityPrincipal principal) {
        catalog.removePackageCollection(packageId, collectionId, principal.userId());
    }
}
