package com.company.exam.commerce.controller;

import com.company.exam.commerce.dto.CommerceDtos;
import com.company.exam.commerce.service.PaymentService;
import com.company.exam.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Provider payment callback ({@code specs/18-P9-Commerce-V1.5.md §29.5, §69}).
 *
 * <p>This endpoint is <strong>not</strong> JWT-protected: it uses the provider's signature
 * mechanism, verified behind {@code PaymentGateway}. A student JWT is never the authenticity
 * mechanism ({@code §69}), so the path is public in the security configuration.
 */
@RestController
@RequestMapping(path = "/api/v1/payments", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Commerce - Payment callback", description = "Provider-facing payment callback")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @Operation(summary = "Process a provider payment callback")
    @PostMapping("/callback")
    public ApiResponse<CommerceDtos.PaymentCallbackResponse> callback(
            @Valid @RequestBody CommerceDtos.PaymentCallbackRequest request) {
        return ApiResponse.ok("Payment callback processed", paymentService.callback(request));
    }
}
