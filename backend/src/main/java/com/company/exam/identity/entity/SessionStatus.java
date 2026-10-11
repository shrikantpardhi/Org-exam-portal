package com.company.exam.identity.entity;

/**
 * Login session lifecycle ({@code specs/04-Database-Schema-V1.5.md §9},
 * {@code specs/10-P1-Identity-V1.5.md §14}).
 *
 * <pre>
 * Login  -> ACTIVE
 * ACTIVE -- logout  --> LOGGED_OUT
 * ACTIVE -- timeout --> EXPIRED
 * </pre>
 */
public enum SessionStatus {
    ACTIVE,
    EXPIRED,
    LOGGED_OUT
}
