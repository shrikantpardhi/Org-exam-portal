package com.company.exam.delivery.api;

import java.util.UUID;

/**
 * The Delivery application contract consumed by Assessment
 * ({@code specs/13-P4-Assessment-V1.5.md §83.1}).
 *
 * <p>{@code delivery_policy} is owned by the Delivery domain; Assessment reads policy existence and
 * metadata through this contract rather than manipulating the Delivery repository directly.
 */
public interface DeliveryPolicyCatalog {

    boolean exists(UUID deliveryPolicyId);
}
