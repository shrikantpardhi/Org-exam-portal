package com.company.exam.identity.api;

import java.util.Optional;
import java.util.UUID;

/**
 * A minimal read-only identity projection used to render display names in result and ranking
 * payloads ({@code specs/15-P6-Evaluation-V1.5.md §99}).
 *
 * <p>It returns only the fields needed for presentation; it never exposes credentials, sessions, or
 * permission data, and it must not be used for authorization.
 */
public interface UserDirectory {

    /** A presentation-safe identity summary. */
    record UserView(UUID userId, String firstName, String lastName, String email) {
    }

    Optional<UserView> find(UUID userId);
}
