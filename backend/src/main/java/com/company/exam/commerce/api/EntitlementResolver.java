package com.company.exam.commerce.api;

import java.time.Instant;
import java.util.UUID;

/**
 * The Commerce entitlement contract consumed by Assessment/Delivery
 * ({@code specs/18-P9-Commerce-V1.5.md §3.2, §9.1, §14, §28.1, §99, §100}).
 *
 * <p>AccessGrant is the authorization source of truth for paid content (§3.2). Other modules ask
 * this contract; they never query the AccessGrant repository (§99).
 */
public interface EntitlementResolver {

    /** Whether the user has a currently valid ACTIVE grant for the exact package ({@code §14.2}). */
    boolean hasActiveGrant(UUID userId, UUID accessPackageId);

    /**
     * The access decision for an assessment, taken at the given server time ({@code §28.1}).
     *
     * <p>An assessment that no AccessPackage grants access to is free content and is allowed for
     * every authenticated user; an assessment a package grants is paid content and requires a
     * currently valid grant for one of those packages. The free rule is recorded in ADR-0026.
     *
     * @param serverNow the authoritative server time used for grant validity ({@code §14.1})
     */
    AccessDecision checkAssessmentAccess(UUID userId, UUID assessmentId, Instant serverNow);

    /**
     * Whether the user may access the assessment ({@code §9.1}). It never infers entitlement from
     * a purchase or a payment record.
     */
    boolean canAccessAssessment(UUID userId, UUID assessmentId);
}
