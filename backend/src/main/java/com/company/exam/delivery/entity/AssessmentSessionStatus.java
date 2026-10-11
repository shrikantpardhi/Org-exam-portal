package com.company.exam.delivery.entity;

/**
 * The delivery runtime session state machine ({@code specs/14-P5-Delivery-V1.5.md §13}).
 *
 * <p>It is intentionally separate from {@code login_session}: an examination session tracks a
 * single runtime attempt, not an authentication session.
 */
public enum AssessmentSessionStatus {
    ACTIVE,
    EXPIRED,
    LOGGED_OUT
}
