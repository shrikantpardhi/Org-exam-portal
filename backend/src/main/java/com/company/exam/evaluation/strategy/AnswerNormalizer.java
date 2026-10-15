package com.company.exam.evaluation.strategy;

import java.util.Locale;

/** Shared, deterministic response normalization for the evaluator strategies ({@code §13, §23}). */
final class AnswerNormalizer {

    private AnswerNormalizer() {
    }

    /** Identity normalization for option keys and UUID option references. */
    static String identity(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    /** Text normalization: trimmed, internal whitespace collapsed, case-insensitive. */
    static String text(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
}
