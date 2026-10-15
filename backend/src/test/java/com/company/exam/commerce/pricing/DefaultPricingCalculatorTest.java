package com.company.exam.commerce.pricing;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.exam.commerce.config.CommerceProperties;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * The pricing calculator ({@code specs/18-P9-Commerce-V1.5.md §15.1, §15.2, §32}).
 *
 * <p>A fixed-amount discount is subtracted from the plan price; it never exceeds the price and the
 * result is never negative.
 */
class DefaultPricingCalculatorTest {

    private final PricingCalculator calculator = new DefaultPricingCalculator(new CommerceProperties());

    private PricingCalculator.PlanSnapshot plan(String price) {
        return new PricingCalculator.PlanSnapshot(UUID.randomUUID(), UUID.randomUUID(), "P", new BigDecimal(price), 30);
    }

    private PricingCalculator.CouponSnapshot coupon(String discount) {
        return new PricingCalculator.CouponSnapshot(UUID.randomUUID(), "C", new BigDecimal(discount));
    }

    @Test
    void fixedDiscountIsSubtractedFromTheListPrice() {
        PricingCalculator.PricingResult result = calculator.calculate(plan("1000.00"), Optional.of(coupon("100.00")));
        assertThat(result.listPrice()).isEqualByComparingTo("1000.00");
        assertThat(result.discount()).isEqualByComparingTo("100.00");
        assertThat(result.finalAmount()).isEqualByComparingTo("900.00");
        assertThat(result.currency()).isEqualTo("INR");
    }

    @Test
    void discountNeverExceedsThePlanPrice() {
        PricingCalculator.PricingResult result = calculator.calculate(plan("50.00"), Optional.of(coupon("500.00")));
        assertThat(result.discount()).isEqualByComparingTo("50.00");
        assertThat(result.finalAmount()).isEqualByComparingTo("0.00");
    }

    @Test
    void amountIsNeverNegative() {
        PricingCalculator.PricingResult result = calculator.calculate(plan("0.00"), Optional.of(coupon("10.00")));
        assertThat(result.finalAmount()).isEqualByComparingTo("0.00");
    }

    @Test
    void noCouponMeansFullPrice() {
        PricingCalculator.PricingResult result = calculator.calculate(plan("999.99"), Optional.empty());
        assertThat(result.discount()).isEqualByComparingTo("0.00");
        assertThat(result.finalAmount()).isEqualByComparingTo("999.99");
    }
}
