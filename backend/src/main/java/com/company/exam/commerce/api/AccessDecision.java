package com.company.exam.commerce.api;

/**
 * The outcome of an assessment access check
 * ({@code specs/18-P9-Commerce-V1.5.md §28.1}).
 *
 * <p>It carries the reason so the caller can distinguish the free path from the granted path
 * without re-deriving the package mapping; the reason is a controlled vocabulary, never free text.
 */
public record AccessDecision(Status status, Reason reason) {

    /** Whether access is permitted. */
    public enum Status {
        ALLOW,
        DENY
    }

    /** Why the decision was reached (ADR-0026). */
    public enum Reason {
        /** No AccessPackage grants access to the assessment, so it is free content. */
        FREE_ASSESSMENT,
        /** The assessment is paid and the user holds a currently valid grant. */
        ACTIVE_GRANT,
        /** The assessment is paid and the user holds no currently valid grant. */
        NO_ACTIVE_GRANT
    }

    public static AccessDecision allow(Reason reason) {
        return new AccessDecision(Status.ALLOW, reason);
    }

    public static AccessDecision deny(Reason reason) {
        return new AccessDecision(Status.DENY, reason);
    }

    public boolean allowed() {
        return status == Status.ALLOW;
    }
}
