package com.company.exam.evaluation.scoring;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * The frozen rounding policy ({@code specs/15-P6-Evaluation-V1.5.md §32, §33, §74}).
 *
 * <p>Marks are academic values, never currency, and are never carried through binary floating point.
 * Per-question awards retain full {@link BigDecimal} precision; only the persisted totals are scaled.
 */
public final class RoundingPolicy {

    /** {@code result.obtained_marks NUMERIC(8,2)}. */
    public static final int MARKS_SCALE = 2;

    /** {@code result.percentage NUMERIC(8,2)}. */
    public static final int PERCENTAGE_SCALE = 2;

    /** {@code result.percentile NUMERIC(8,4)}. */
    public static final int PERCENTILE_SCALE = 4;

    public static final int RANKING_SCALE = PERCENTAGE_SCALE;

    private RoundingPolicy() {
    }

    /** HALF_UP to 2 decimals, matching the persisted {@code obtained_marks} scale. */
    public static BigDecimal marks(BigDecimal value) {
        return value == null ? null : value.setScale(MARKS_SCALE, RoundingMode.HALF_UP);
    }

    /** HALF_UP to 2 decimals, matching the persisted {@code percentage} scale. */
    public static BigDecimal percentage(BigDecimal value) {
        return value == null ? null : value.setScale(PERCENTAGE_SCALE, RoundingMode.HALF_UP);
    }

    /** HALF_UP to 4 decimals, matching the persisted {@code percentile} scale. */
    public static BigDecimal percentile(BigDecimal value) {
        return value == null ? null : value.setScale(PERCENTILE_SCALE, RoundingMode.HALF_UP);
    }
}
