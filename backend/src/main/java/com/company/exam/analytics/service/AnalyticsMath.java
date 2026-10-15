package com.company.exam.analytics.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Rounding and bound helpers for analytics ({@code specs/17-P8-Analytics-V1.5.md §115-§117}).
 *
 * <p>All analytics arithmetic uses {@link BigDecimal}; no binary floating point is used for a
 * persisted metric. A metric with no population is {@code null} rather than a misleading zero
 * ({@code §90}).
 */
final class AnalyticsMath {

    static final int SCORE_SCALE = 2;
    static final int PERCENTAGE_SCALE = 2;
    static final int TIME_SCALE = 2;
    static final int DIFFICULTY_SCALE = 4;

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private AnalyticsMath() {
    }

    static BigDecimal score(BigDecimal value) {
        return value == null ? null : value.setScale(SCORE_SCALE, RoundingMode.HALF_UP);
    }

    static BigDecimal percentage(BigDecimal value) {
        return value == null ? null : value.setScale(PERCENTAGE_SCALE, RoundingMode.HALF_UP);
    }

    static BigDecimal time(BigDecimal value) {
        return value == null ? null : value.setScale(TIME_SCALE, RoundingMode.HALF_UP);
    }

    static BigDecimal difficulty(BigDecimal value) {
        return value == null ? null : value.setScale(DIFFICULTY_SCALE, RoundingMode.HALF_UP);
    }

    /** Mean of the values, or {@code null} when the population is empty ({@code §90}). */
    static BigDecimal mean(java.util.List<BigDecimal> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        BigDecimal sum = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(values.size()), 10, RoundingMode.HALF_UP);
    }

    /** {@code correct / (correct + incorrect) * 100}, bounded to [0, 100], or {@code null} with no denominator. */
    static BigDecimal accuracy(long correct, long incorrect) {
        long denominator = correct + incorrect;
        if (denominator == 0) {
            return null;
        }
        BigDecimal value = BigDecimal.valueOf(correct).multiply(HUNDRED)
                .divide(BigDecimal.valueOf(denominator), PERCENTAGE_SCALE, RoundingMode.HALF_UP);
        return percentage(clamp(value));
    }

    /** {@code part / total * 100}, bounded to [0, 100], or {@code null} when total is zero. */
    static BigDecimal share(long part, long total) {
        if (total == 0) {
            return null;
        }
        BigDecimal value = BigDecimal.valueOf(part).multiply(HUNDRED)
                .divide(BigDecimal.valueOf(total), PERCENTAGE_SCALE, RoundingMode.HALF_UP);
        return percentage(clamp(value));
    }

    /**
     * The frozen difficulty index: {@code 1 - (correct / total)}, bounded to [0, 1], or {@code null}
     * when the denominator is empty ({@code specs/17-P8-Analytics-V1.5.md §35}).
     */
    static BigDecimal difficultyIndex(long correct, long total) {
        if (total == 0) {
            return null;
        }
        BigDecimal ratio = BigDecimal.valueOf(correct)
                .divide(BigDecimal.valueOf(total), 10, RoundingMode.HALF_UP);
        return difficulty(clampUnit(BigDecimal.ONE.subtract(ratio)));
    }

    static BigDecimal clamp(BigDecimal value) {
        if (value.signum() < 0) {
            return BigDecimal.ZERO;
        }
        return value.compareTo(HUNDRED) > 0 ? HUNDRED : value;
    }

    static BigDecimal clampUnit(BigDecimal value) {
        if (value.signum() < 0) {
            return BigDecimal.ZERO;
        }
        return value.compareTo(BigDecimal.ONE) > 0 ? BigDecimal.ONE : value;
    }
}
