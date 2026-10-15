package com.company.exam.administration.api;

import java.util.List;
import java.util.Optional;

/**
 * The lookup contract consumed by other modules
 * ({@code specs/20-P11-Administration-V1.5.md §10, §89}).
 *
 * <p>Consumers reference category and code; display names are presentation data. Backend
 * calculations must not depend on mutable names — use {@link #isActive} for a boolean decision and
 * {@link #resolveName} only for display.
 */
public interface LookupCatalog {

    /** A lookup value. */
    record LookupValue(String category, String code, String name, Integer displayOrder, boolean active) {
    }

    /** ACTIVE values of a category, ordered by display order. */
    List<LookupValue> values(String category);

    Optional<LookupValue> find(String category, String code);

    /** Whether the category/code pair exists and is ACTIVE. */
    default boolean isActive(String category, String code) {
        return find(category, code).map(LookupValue::active).orElse(false);
    }

    /** The display name of an ACTIVE value, for presentation only. */
    default Optional<String> resolveName(String category, String code) {
        return find(category, code).filter(LookupValue::active).map(LookupValue::name);
    }
}
