package com.company.exam.administration.api;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

/**
 * The typed business-rule resolver consumed by owning modules
 * ({@code specs/20-P11-Administration-V1.5.md §9.7}).
 *
 * <p>Administration owns persistence; the consumer owns business semantics ({@code §88}). Rule
 * configuration is data, never executable code ({@code §9.3}). A missing or non-ACTIVE rule returns
 * empty so the consumer applies its documented safe default ({@code §9.4}).
 */
public interface BusinessRuleResolver {

    Optional<ResolvedRule> resolve(String ruleCode);

    /** A resolved, data-only rule configuration. */
    record ResolvedRule(String code, Map<String, Object> values) {

        public Optional<String> string(String key) {
            return read(key, String.class);
        }

        public Optional<Boolean> bool(String key) {
            return read(key, Boolean.class);
        }

        /** Reads a numeric value, tolerating its JSON number or string representation. */
        public Optional<BigDecimal> decimal(String key) {
            Object value = values == null ? null : values.get(key);
            if (value instanceof Number number) {
                return Optional.of(new BigDecimal(number.toString()));
            }
            if (value instanceof String text) {
                try {
                    return Optional.of(new BigDecimal(text));
                } catch (NumberFormatException notNumeric) {
                    return Optional.empty();
                }
            }
            return Optional.empty();
        }

        private <T> Optional<T> read(String key, Class<T> type) {
            Object value = values == null ? null : values.get(key);
            return type.isInstance(value) ? Optional.of(type.cast(value)) : Optional.empty();
        }
    }
}
