package com.company.exam.commerce.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Commerce request and response payloads ({@code specs/18-P9-Commerce-V1.5.md §46-§51}).
 *
 * <p>Entities are never exposed. Provider-specific payloads, secrets, and audit metadata never
 * appear here; student projections omit other-user identifiers ({@code §47, §50}).
 */
public final class CommerceDtos {

    private CommerceDtos() {
    }

    // ---------------------------------------------------------------- Access package

    public record CreateAccessPackageRequest(@NotBlank @Size(max = 50) String code,
            @NotBlank @Size(max = 200) String name, String description) {
    }

    public record UpdateAccessPackageRequest(@Size(max = 200) String name, String description) {
    }

    public record AccessPackageResponse(UUID id, String code, String name, String description, String status) {
    }

    public record AddPackageCollectionRequest(@NotNull UUID collectionId) {
    }

    public record PackageCollectionResponse(UUID id, UUID accessPackageId, UUID assessmentCollectionId,
            String status) {
    }

    // ---------------------------------------------------------------- Subscription plan

    public record CreateSubscriptionPlanRequest(@NotNull UUID accessPackageId, @NotBlank @Size(max = 50) String code,
            @NotBlank @Size(max = 150) String name, @NotNull @Positive Integer durationDays,
            @NotNull @PositiveOrZero BigDecimal price) {
    }

    public record UpdateSubscriptionPlanRequest(@Size(max = 150) String name, @Positive Integer durationDays,
            @PositiveOrZero BigDecimal price) {
    }

    public record SubscriptionPlanResponse(UUID id, UUID accessPackageId, String code, String name,
            Integer durationDays, BigDecimal price, String status) {
    }

    // ---------------------------------------------------------------- Coupon

    public record CreateCouponRequest(@NotBlank @Size(max = 50) String code, @NotBlank @Size(max = 150) String name,
            @NotNull @PositiveOrZero BigDecimal discountValue, Instant validFrom, Instant validTo) {
    }

    public record UpdateCouponRequest(@Size(max = 150) String name, @PositiveOrZero BigDecimal discountValue,
            Instant validFrom, Instant validTo) {
    }

    public record CouponResponse(UUID id, String code, String name, BigDecimal discountValue, Instant validFrom,
            Instant validTo, String status) {
    }

    public record ValidateCouponRequest(@NotBlank String code, @NotNull UUID subscriptionPlanId) {
    }

    public record ValidateCouponResponse(boolean valid, String code, UUID subscriptionPlanId, BigDecimal listPrice,
            BigDecimal discount, BigDecimal finalAmount, String currency, String reason) {
    }

    // ---------------------------------------------------------------- Purchase

    public record CreatePurchaseRequest(@NotNull UUID subscriptionPlanId, String couponCode) {
    }

    /** Student-safe purchase projection ({@code §47}): no audit metadata, no other-user identifiers. */
    public record PurchaseResponse(UUID id, UUID subscriptionPlanId, UUID accessPackageId, String planName,
            UUID couponId, Instant purchaseDate, BigDecimal amount, String paymentStatus) {
    }

    // ---------------------------------------------------------------- Payment

    public record PaymentInitiationResponse(UUID purchaseId, String paymentStatus, String paymentReference,
            String checkoutToken, String nextAction) {
    }

    public record PaymentView(UUID purchaseId, String paymentStatus, String paymentReference, String paymentMethod,
            Instant paymentDate) {
    }

    public record PaymentCallbackRequest(@NotBlank String paymentReference, @NotBlank String providerStatus,
            @NotNull BigDecimal amount, String currency, String signature) {
    }

    public record PaymentCallbackResponse(String paymentReference, UUID purchaseId, String paymentStatus,
            boolean accessGranted, boolean duplicate) {
    }

    // ---------------------------------------------------------------- Access grant

    public record CreateAccessGrantRequest(@NotNull UUID userId, @NotNull UUID accessPackageId,
            @NotNull Instant startDate, Instant endDate) {
    }

    public record AccessGrantResponse(UUID id, UUID userId, UUID accessPackageId, String packageName,
            Instant startDate, Instant endDate, String status, boolean active) {
    }

    // ---------------------------------------------------------------- Refund

    public record RefundResponse(UUID purchaseId, String paymentStatus, boolean refunded) {
    }

    // ---------------------------------------------------------------- Reconciliation

    public record ReconciliationFinding(String paymentReference, UUID purchaseId, String localStatus,
            String providerStatus, String state) {
    }

    public record ReconciliationReport(int checked, int mismatches, List<ReconciliationFinding> findings) {
    }
}
