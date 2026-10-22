package com.company.exam.commerce.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The Commerce read contract consumed by Reporting
 * ({@code specs/22-P13-Reporting-V1.5.md §10, §56-§62}).
 *
 * <p>Reporting reads purchases, payments, access grants and coupon usage through this contract and
 * never through Commerce repositories ({@code §10}). It exposes no provider credential, token or
 * raw gateway payload ({@code §82}).
 *
 * <p>Every method is read-only and date-bounded by the caller ({@code §7.3, §7.6}). A student caller
 * is scoped to their own user id by the caller before the read happens ({@code §76}).
 */
public interface ReportingCommerceCatalog {

    /** One purchase row (§56). */
    record PurchaseRow(UUID purchaseId, UUID userId, UUID subscriptionPlanId, UUID couponId,
            Instant purchaseDate, BigDecimal amount, String paymentStatus) {
    }

    /** One payment row (§57). The provider reference is included; no secret is exposed. */
    record PaymentRow(UUID paymentId, UUID purchaseId, String paymentReference, String paymentMethod,
            Instant paymentDate, String paymentStatus) {
    }

    /** One access-grant row (§58). */
    record AccessGrantRow(UUID grantId, UUID userId, UUID accessPackageId, Instant startDate, Instant endDate,
            String status) {
    }

    /** Purchases in a window, optionally narrowed to one user, bounded by {@code limit}. */
    List<PurchaseRow> purchases(UUID userId, Instant from, Instant to, int limit);

    /** Payments whose purchase falls in the window, bounded by {@code limit}. */
    List<PaymentRow> payments(Instant from, Instant to, int limit);

    /** Access grants started in the window, optionally narrowed to one user. */
    List<AccessGrantRow> accessGrants(UUID userId, Instant from, Instant to, int limit);
}
