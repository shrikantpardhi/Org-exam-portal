package com.company.exam.identity;

import java.util.UUID;

/** Well-known Identity constants ({@code specs/10-P1-Identity-V1.5.md §15}). */
public final class IdentityConstants {

    /** The system actor that represents background and scheduled operations. */
    public static final UUID SYSTEM_ACTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    /** The system actor's email; login is blocked for it. */
    public static final String SYSTEM_ACTOR_EMAIL = "system@platform.internal";

    /** Permission required to grant or revoke the ADMIN role ({@code §63, §65}). */
    public static final String ADMIN_ROLE_ASSIGN = "ADMIN_ROLE_ASSIGN";

    /** Role code of the administrator role ({@code §64}). */
    public static final String ADMIN_ROLE_CODE = "ADMIN";

    /** Role code of the installation-owner role (ADR-0032). */
    public static final String SUPER_ADMIN_ROLE_CODE = "SUPER_ADMIN";

    /**
     * Role codes whose shape is fixed by the deployment contract and which no caller may rename,
     * inactivate, or re-permission ({@code 20-P11} §18, ADR-0032, ADR-0038 / D-02).
     *
     * <p>Resolved by code rather than by seeded UUID so the rule survives a re-seed (G-02).
     */
    public static final java.util.Set<String> IMMUTABLE_ROLE_CODES = java.util.Set.of(SUPER_ADMIN_ROLE_CODE);

    private IdentityConstants() {
    }
}
