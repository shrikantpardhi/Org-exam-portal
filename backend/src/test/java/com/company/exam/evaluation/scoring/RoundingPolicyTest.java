package com.company.exam.evaluation.scoring;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** Rounding policy ({@code specs/15-P6-Evaluation-V1.5.md §32}). */
class RoundingPolicyTest {

    @Test
    void marksRoundHalfUpToTwoDecimals() {
        assertThat(RoundingPolicy.marks(new BigDecimal("2.345"))).isEqualByComparingTo("2.35");
        assertThat(RoundingPolicy.marks(new BigDecimal("2.344"))).isEqualByComparingTo("2.34");
        assertThat(RoundingPolicy.marks(null)).isNull();
    }

    @Test
    void percentageRoundsHalfUpToTwoDecimals() {
        assertThat(RoundingPolicy.percentage(new BigDecimal("66.666"))).isEqualByComparingTo("66.67");
    }

    @Test
    void percentileRoundsHalfUpToFourDecimals() {
        assertThat(RoundingPolicy.percentile(new BigDecimal("99.98195"))).isEqualByComparingTo("99.9820");
        assertThat(RoundingPolicy.percentile(new BigDecimal("33.333333"))).isEqualByComparingTo("33.3333");
    }
}
