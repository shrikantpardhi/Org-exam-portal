package com.company.exam.commerce.controller;

import com.company.exam.commerce.dto.CommerceDtos;
import com.company.exam.commerce.service.PaymentService;
import com.company.exam.commerce.service.PurchaseService;
import com.company.exam.commerce.service.RefundService;
import com.company.exam.common.api.ApiResponse;
import com.company.exam.identity.security.IdentityPrincipal;
import com.company.exam.identity.service.AuthorizationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Purchase, payment initiation, and refund API
 * ({@code specs/18-P9-Commerce-V1.5.md §29.4-§29.6, §30, §67}).
 *
 * <p>Students see and pay only their own purchases; a finance/admin caller with {@code PURCHASE_VIEW}
 * sees any purchase. Refund requires {@code PURCHASE_REFUND}.
 */
@RestController
@RequestMapping(path = "/api/v1/purchases", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Commerce - Purchases", description = "Purchases, payment initiation and refunds")
public class PurchaseController {

    private final PurchaseService purchaseService;
    private final PaymentService paymentService;
    private final RefundService refundService;
    private final AuthorizationService authorizationService;

    public PurchaseController(PurchaseService purchaseService, PaymentService paymentService,
            RefundService refundService, AuthorizationService authorizationService) {
        this.purchaseService = purchaseService;
        this.paymentService = paymentService;
        this.refundService = refundService;
        this.authorizationService = authorizationService;
    }

    @Operation(summary = "Create a purchase")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CommerceDtos.PurchaseResponse> create(
            @Valid @RequestBody CommerceDtos.CreatePurchaseRequest request,
            @AuthenticationPrincipal IdentityPrincipal principal) {
        return ApiResponse.ok("Purchase created", purchaseService.create(principal.userId(), request));
    }

    @Operation(summary = "List my purchases")
    @GetMapping
    public ApiResponse<List<CommerceDtos.PurchaseResponse>> list(@AuthenticationPrincipal IdentityPrincipal principal) {
        return ApiResponse.ok(purchaseService.listForUser(principal.userId()));
    }

    @Operation(summary = "Get a purchase")
    @GetMapping("/{id}")
    public ApiResponse<CommerceDtos.PurchaseResponse> get(@PathVariable UUID id, Authentication authentication,
            @AuthenticationPrincipal IdentityPrincipal principal) {
        boolean staff = authorizationService.hasPermission(authentication, "PURCHASE_VIEW");
        return ApiResponse.ok(purchaseService.getVisible(id, principal.userId(), staff));
    }

    @Operation(summary = "Initiate payment for my purchase")
    @PostMapping("/{purchaseId}/payment")
    public ApiResponse<CommerceDtos.PaymentInitiationResponse> initiatePayment(@PathVariable UUID purchaseId,
            @AuthenticationPrincipal IdentityPrincipal principal) {
        return ApiResponse.ok("Payment initiated",
                paymentService.initiate(principal.userId(), purchaseId));
    }

    @Operation(summary = "Get the payment of my purchase")
    @GetMapping("/{purchaseId}/payment")
    public ApiResponse<CommerceDtos.PaymentView> getPayment(@PathVariable UUID purchaseId,
            @AuthenticationPrincipal IdentityPrincipal principal) {
        return ApiResponse.ok(paymentService.get(principal.userId(), purchaseId));
    }

    @Operation(summary = "Refund a purchase")
    @PostMapping("/{purchaseId}/refund")
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'PURCHASE_REFUND')")
    public ApiResponse<CommerceDtos.RefundResponse> refund(@PathVariable UUID purchaseId,
            @AuthenticationPrincipal IdentityPrincipal principal) {
        return ApiResponse.ok("Purchase refunded", refundService.refund(purchaseId, principal.userId()));
    }
}
