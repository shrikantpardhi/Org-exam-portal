package com.company.exam.delivery.engine;

/**
 * The typed, validated attributes of a {@code delivery_policy.configuration} JSON object
 * ({@code specs/14-P5-Delivery-V1.5.md §12, §35}).
 *
 * <p>Only the known declarative attributes are carried; any unsupported key fails safe rather than
 * being silently interpreted.
 */
public record DeliveryPolicyAttributes(
        boolean allowBackNavigation,
        boolean allowSectionSwitch,
        int autoSaveIntervalSeconds,
        boolean allowQuestionReview,
        boolean allowClearResponse,
        boolean allowMarkForReview,
        boolean autoSubmitOnExpiry) {

    public static DeliveryPolicyAttributes defaults() {
        return new DeliveryPolicyAttributes(true, true, 10, true, true, true, false);
    }
}
