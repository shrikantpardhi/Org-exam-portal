package com.company.exam.commerce.pricing;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/**
 * The server-side pricing boundary ({@code specs/18-P9-Commerce-V1.5.md §11.1, §32}).
 *
 * <p>Only trusted inputs are used: the authoritative plan price and the validated coupon record.
 * Client-supplied subtotal, price, discount, or total are never accepted.
 */
public interface PricingCalculator {

    /** The authoritative plan price input. */
    record PlanSnapshot(UUID planId, UUID accessPackageId, String code, BigDecimal price, int durationDays) {
    }

    /** The validated coupon input. */
    record CouponSnapshot(UUID couponId, String code, BigDecimal discountValue) {
    }

    /** The calculated price. Only {@code finalAmount} is persisted by the frozen schema ({@code §15.2}). */
    record PricingResult(BigDecimal listPrice, BigDecimal discount, BigDecimal finalAmount, String currency,
            String calculationVersion) {
    }

    PricingResult calculate(PlanSnapshot plan, Optional<CouponSnapshot> coupon);
}
