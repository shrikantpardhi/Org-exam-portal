package com.company.exam.administration.api;

import java.util.Optional;
import java.util.UUID;

/**
 * The organization-configuration contract consumed by other modules
 * ({@code specs/20-P11-Administration-V1.5.md §8.2, §88}).
 *
 * <p>Administration owns storage; the consumer module owns semantics. Consumers resolve typed
 * configuration through this contract instead of parsing JSONB across the codebase. A missing
 * document means the consumer falls back to its own safe default.
 */
public interface ConfigurationCatalog {

    /** A resolved organization setting document, or empty when the organization has none. */
    Optional<OrganizationConfiguration> organizationConfiguration(UUID organizationId);

    /** An organization setting document: recognized keys only, validated before persistence. */
    record OrganizationConfiguration(UUID organizationId, java.util.Map<String, Object> values) {

        public Optional<String> string(String key) {
            return read(key, String.class);
        }

        public Optional<Boolean> bool(String key) {
            return read(key, Boolean.class);
        }

        public Optional<Integer> integer(String key) {
            return read(key, Integer.class);
        }

        private <T> Optional<T> read(String key, Class<T> type) {
            Object value = values == null ? null : values.get(key);
            return type.isInstance(value) ? Optional.of(type.cast(value)) : Optional.empty();
        }
    }
}
