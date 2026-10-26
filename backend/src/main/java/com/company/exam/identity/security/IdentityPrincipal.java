package com.company.exam.identity.security;

import java.security.Principal;
import java.util.UUID;

/**
 * The minimum runtime identity carried in the security context
 * ({@code specs/10-P1-Identity-V1.5.md §37}).
 *
 * <p>It is deliberately small: no role graph, no permission graph. Permissions are resolved per
 * request by the authorization service. Implementing {@link Principal} makes
 * {@code Authentication.getName()} return the user's UUID, which the JPA audit provider consumes.
 *
 * @param userId             the authenticated user's id
 * @param sessionId          the login session id, or {@code null} for a session-less token
 * @param mustChangePassword whether the account must change its password before reaching other
 *                           resources
 */
public record IdentityPrincipal(UUID userId, UUID sessionId, boolean mustChangePassword) implements Principal {

    @Override
    public String getName() {
        return userId.toString();
    }
}
