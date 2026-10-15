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
 * Coupon API ({@code specs/18-P9-Commerce-V1.5.md §29.8, §33}).
 *
 * <p>Validation is available to any authenticated user for checkout; administration is
 * permission-gated. The validation response is not trusted at purchase creation.
 */
@RestController
@RequestMapping(path = "/api/v1/coupons", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Commerce - Coupons", description = "Fixed-amount discounts")
public class CouponController {

    private final CommerceCatalogService catalog;

    public CouponController(CommerceCatalogService catalog) {
        this.catalog = catalog;
    }

    @Operation(summary = "List coupons")
    @GetMapping
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'COUPON_VIEW')")
    public ApiResponse<List<CommerceDtos.CouponResponse>> list(@RequestParam(required = false) Status status) {
        return ApiResponse.ok(catalog.listCoupons(status));
    }

    @Operation(summary = "Get a coupon")
    @GetMapping("/{id}")
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'COUPON_VIEW')")
    public ApiResponse<CommerceDtos.CouponResponse> get(@PathVariable UUID id) {
        return ApiResponse.ok(catalog.getCoupon(id));
    }

    @Operation(summary = "Create a coupon")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'COUPON_MANAGE')")
    public ApiResponse<CommerceDtos.CouponResponse> create(@Valid @RequestBody CommerceDtos.CreateCouponRequest request,
            @AuthenticationPrincipal IdentityPrincipal principal) {
        return ApiResponse.ok("Coupon created", catalog.createCoupon(request, principal.userId()));
    }

    @Operation(summary = "Update a coupon")
    @PutMapping("/{id}")
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'COUPON_MANAGE')")
    public ApiResponse<CommerceDtos.CouponResponse> update(@PathVariable UUID id,
            @Valid @RequestBody CommerceDtos.UpdateCouponRequest request,
            @AuthenticationPrincipal IdentityPrincipal principal) {
        return ApiResponse.ok("Coupon updated", catalog.updateCoupon(id, request, principal.userId()));
    }

    @Operation(summary = "Archive a coupon")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'COUPON_MANAGE')")
    public void archive(@PathVariable UUID id, @AuthenticationPrincipal IdentityPrincipal principal) {
        catalog.archiveCoupon(id, principal.userId());
    }

    @Operation(summary = "Validate a coupon against a plan")
    @PostMapping("/validate")
    public ApiResponse<CommerceDtos.ValidateCouponResponse> validate(
            @Valid @RequestBody CommerceDtos.ValidateCouponRequest request) {
        return ApiResponse.ok("Coupon validated", catalog.validateCoupon(request));
    }
}
