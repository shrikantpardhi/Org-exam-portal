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

    private IdentityConstants() {
    }
}
