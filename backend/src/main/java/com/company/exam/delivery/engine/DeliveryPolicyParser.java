package com.company.exam.delivery.engine;

import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Parses and validates a {@code delivery_policy.configuration} JSON object into
 * {@link DeliveryPolicyAttributes} ({@code specs/14-P5-Delivery-V1.5.md §34, §35}).
 *
 * <p>The platform {@link ObjectMapper} rejects unknown fields, so a configuration that carries
 * executable or unsupported keys fails safe to the defaults instead of being interpreted. A
 * malformed or missing configuration also resolves to the safe defaults.
 */
@Component
public class DeliveryPolicyParser {

    private final ObjectMapper objectMapper;

    public DeliveryPolicyParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public DeliveryPolicyAttributes parse(String configurationJson) {
        if (configurationJson == null || configurationJson.isBlank()) {
            return DeliveryPolicyAttributes.defaults();
        }
        try {
            RawDeliveryPolicy raw = objectMapper.readValue(configurationJson, RawDeliveryPolicy.class);
            DeliveryPolicyAttributes defaults = DeliveryPolicyAttributes.defaults();
            return new DeliveryPolicyAttributes(
                    defaultIfNull(raw.allowBackNavigation, defaults.allowBackNavigation()),
                    defaultIfNull(raw.allowSectionSwitch, defaults.allowSectionSwitch()),
                    raw.autoSaveIntervalSeconds == null ? defaults.autoSaveIntervalSeconds()
                            : raw.autoSaveIntervalSeconds,
                    defaultIfNull(raw.allowQuestionReview, defaults.allowQuestionReview()),
                    defaultIfNull(raw.allowClearResponse, defaults.allowClearResponse()),
                    defaultIfNull(raw.allowMarkForReview, defaults.allowMarkForReview()),
                    defaultIfNull(raw.autoSubmitOnExpiry, defaults.autoSubmitOnExpiry()));
        } catch (Exception malformedOrForbidden) {
            return DeliveryPolicyAttributes.defaults();
        }
    }

    private boolean defaultIfNull(Boolean value, boolean fallback) {
        return value == null ? fallback : value;
    }

    /** Typed, optionally-populated view; unknown fields are rejected by the platform mapper. */
    record RawDeliveryPolicy(
            Boolean allowBackNavigation,
            Boolean allowSectionSwitch,
            Integer autoSaveIntervalSeconds,
            Boolean allowQuestionReview,
            Boolean allowClearResponse,
            Boolean allowMarkForReview,
            Boolean autoSubmitOnExpiry) {
    }
}
