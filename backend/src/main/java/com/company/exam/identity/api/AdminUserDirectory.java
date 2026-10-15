package com.company.exam.identity.api;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The Identity administration contract consumed by the Administration module
 * ({@code specs/20-P11-Administration-V1.5.md §12-§16, §37, §39}).
 *
 * <p>Administration never mutates the {@code user} table itself: lifecycle changes call Identity,
 * which owns ACTIVE/INACTIVE/ARCHIVED and the permission cache ({@code §14, §16, §82, §83}).
 *
 * <p>It returns identity and contact data only. It never exposes password hashes, reset tokens,
 * sessions, or permission internals ({@code §36}).
 */
public interface AdminUserDirectory {

    /** An administrative identity projection. Contact values are raw; masking is a presentation rule. */
    record AdminUserView(UUID userId, String firstName, String lastName, String email, String mobile,
            String status) {
    }

    Optional<AdminUserView> find(UUID userId);

    /**
     * Searches the frozen USER fields: first and last name (prefix/contains), email (exact) and
     * mobile (exact) ({@code §39}). Results are bounded by {@code limit}.
     */
    List<AdminUserView> search(String query, int limit);

    /** Sets the Identity-owned lifecycle state; audited by Identity. */
    void setLifecycle(UUID userId, String status, UUID actorId);

    long countActiveUsers();
}
