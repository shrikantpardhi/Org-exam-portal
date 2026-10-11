package com.company.exam.common.audit;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Resolves the current auditor for JPA audit fields ({@code created_by}, {@code updated_by}).
 *
 * <p>P0 establishes the mechanism but does not implement the Identity domain. The JWT
 * authentication boundary sets the principal to the token subject; P1 Identity guarantees that
 * subject is the user's UUID. Until then, a non-UUID principal resolves to
 * {@link Optional#empty()} rather than a misleading attribution.
 *
 * <p>{@link #SYSTEM_ACTOR_ID} is the well-known system identity used by background and scheduled
 * operations ({@code specs/03-Technical-Design-Document-V1.5.md §31}); P1 wires background
 * operations to it explicitly.
 */
@Component
public class CurrentAuditorProvider implements AuditorAware<UUID> {

    /** Well-known system actor identity for background and scheduled operations. */
    public static final UUID SYSTEM_ACTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Override
    public Optional<UUID> getCurrentAuditor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        String principal = authentication.getName();
        if (principal == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(principal));
        } catch (IllegalArgumentException notAUuid) {
            return Optional.empty();
        }
    }
}
