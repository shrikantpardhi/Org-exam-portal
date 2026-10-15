package com.company.exam.commerce.api;

import java.util.UUID;

/**
 * The Commerce entitlement contract consumed by Assessment/Delivery
 * ({@code specs/18-P9-Commerce-V1.5.md §14, §99, §100}).
 *
 * <p>AccessGrant is the authorization source of truth for paid content ({@code §3.2}). Other modules
 * ask this contract; they never query the AccessGrant repository ({@code §99}).
 */
public interface EntitlementResolver {

    /** Whether the user has a currently valid ACTIVE grant for the exact package ({@code §14.2}). */
    boolean hasActiveGrant(UUID userId, UUID accessPackageId);

    /**
     * Whether the user is entitled to the assessment through a package-to-collection grant
     * ({@code §9.1}). It never infers entitlement from a purchase or a payment record.
     */
    boolean canAccessAssessment(UUID userId, UUID assessmentId);
}
