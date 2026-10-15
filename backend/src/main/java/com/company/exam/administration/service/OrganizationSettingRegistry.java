package com.company.exam.administration.service;

import com.company.exam.common.exception.ValidationException;
import com.company.exam.common.api.ApiError;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * The recognized organization-setting key registry
 * ({@code specs/20-P11-Administration-V1.5.md §8.3, §8.6}).
 *
 * <p>A syntactically valid JSON document is not necessarily a valid organization configuration
 * ({@code §8.4}). Only registered keys are accepted, each with a declared type, default, mutability,
 * and security classification. No module silently invents a configuration key.
 */
@Component
public class OrganizationSettingRegistry {

    /** One registered key. */
    public record SettingKey(String key, Class<?> type, Object defaultValue, boolean mutable,
            String securityClassification) {
    }

    private static final Map<String, SettingKey> KEYS = registered();

    private static Map<String, SettingKey> registered() {
        Map<String, SettingKey> keys = new LinkedHashMap<>();
        register(keys, "display.dateFormat", String.class, "YYYY-MM-DD", true, "PUBLIC");
        register(keys, "display.timeZone", String.class, "UTC", true, "PUBLIC");
        register(keys, "notifications.emailEnabled", Boolean.class, Boolean.TRUE, true, "INTERNAL");
        register(keys, "operations.maintenanceMode", Boolean.class, Boolean.FALSE, true, "PUBLIC");
        register(keys, "operations.maintenanceMessage", String.class, "", true, "PUBLIC");
        return Map.copyOf(keys);
    }

    private static void register(Map<String, SettingKey> keys, String key, Class<?> type, Object defaultValue,
            boolean mutable, String classification) {
        keys.put(key, new SettingKey(key, type, defaultValue, mutable, classification));
    }

    public List<SettingKey> descriptors() {
        return List.copyOf(KEYS.values());
    }

    public Set<String> recognizedKeys() {
        return KEYS.keySet();
    }

    /** Overlays registered defaults under the supplied values, so consumers always see a full document. */
    public Map<String, Object> withDefaults(Map<String, Object> supplied) {
        Map<String, Object> merged = new LinkedHashMap<>();
        KEYS.forEach((key, descriptor) -> merged.put(key, descriptor.defaultValue()));
        if (supplied != null) {
            merged.putAll(supplied);
        }
        return merged;
    }

    /**
     * Validates a supplied document: it must be a JSON object, every key must be registered, every
     * value must match the declared type, and cross-field rules must hold ({@code §8.4}).
     */
    public void validate(Map<String, Object> supplied) {
        if (supplied == null) {
            throw new ValidationException("Configuration is required",
                    List.of(ApiError.of("configuration", "CONFIG_REQUIRED", "A configuration object is required")));
        }
        List<ApiError> errors = new java.util.ArrayList<>();
        for (Map.Entry<String, Object> entry : supplied.entrySet()) {
            SettingKey descriptor = KEYS.get(entry.getKey());
            if (descriptor == null) {
                errors.add(ApiError.of(entry.getKey(), "CONFIG_KEY_UNKNOWN",
                        "Unrecognized configuration key: " + entry.getKey()));
                continue;
            }
            if (!descriptor.mutable()) {
                errors.add(ApiError.of(entry.getKey(), "CONFIG_KEY_IMMUTABLE",
                        "Configuration key cannot be changed: " + entry.getKey()));
                continue;
            }
            if (entry.getValue() != null && !descriptor.type().isInstance(entry.getValue())) {
                errors.add(ApiError.of(entry.getKey(), "CONFIG_VALUE_TYPE",
                        "Expected " + descriptor.type().getSimpleName() + " for " + entry.getKey()));
            }
        }
        errors.addAll(crossFieldErrors(supplied));
        if (!errors.isEmpty()) {
            throw new ValidationException("Configuration validation failed", errors);
        }
    }

    /** Cross-field rules: a maintenance banner is required while maintenance mode is on. */
    private List<ApiError> crossFieldErrors(Map<String, Object> supplied) {
        if (Boolean.TRUE.equals(supplied.get("operations.maintenanceMode"))) {
            Object message = supplied.get("operations.maintenanceMessage");
            if (!(message instanceof String text) || text.isBlank()) {
                return List.of(ApiError.of("operations.maintenanceMessage", "CONFIG_MAINTENANCE_MESSAGE_REQUIRED",
                        "A maintenance message is required while maintenance mode is enabled"));
            }
        }
        return List.of();
    }
}
