package com.company.exam.commerce.pricing;

import com.company.exam.commerce.config.CommerceProperties;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * The default {@link PricingCalculator} ({@code specs/18-P9-Commerce-V1.5.md §15.1, §15.2, §32}).
 *
 * <p>The coupon discount is a <strong>fixed amount</strong> subtracted from the plan price. It can
 * never exceed the plan price and the result is never negative. Rounding is HALF_UP at 2 decimals.
 * Only {@link BigDecimal} is used; no floating point.
 */
@Component
public class DefaultPricingCalculator implements PricingCalculator {

    private static final int SCALE = 2;

    private final CommerceProperties properties;

    public DefaultPricingCalculator(CommerceProperties properties) {
        this.properties = properties;
    }

    @Override
    public PricingResult calculate(PlanSnapshot plan, Optional<CouponSnapshot> coupon) {
        BigDecimal listPrice = plan.price() == null ? BigDecimal.ZERO : plan.price()
                .setScale(SCALE, RoundingMode.HALF_UP);
        BigDecimal discount = coupon
                .map(value -> value.discountValue() == null ? BigDecimal.ZERO : value.discountValue())
                .map(value -> value.setScale(SCALE, RoundingMode.HALF_UP))
                .orElse(BigDecimal.ZERO);
        if (discount.signum() < 0) {
            discount = BigDecimal.ZERO;
        }
        if (discount.compareTo(listPrice) > 0) {
            discount = listPrice;
        }
        BigDecimal finalAmount = listPrice.subtract(discount).setScale(SCALE, RoundingMode.HALF_UP);
        if (finalAmount.signum() < 0) {
            finalAmount = BigDecimal.ZERO.setScale(SCALE, RoundingMode.HALF_UP);
        }
        return new PricingResult(listPrice, discount, finalAmount, properties.getCurrency(),
                properties.getCalculationVersion());
    }
}
