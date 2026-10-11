package com.company.exam.delivery.entity;

/**
 * The separate snapshot state machine on {@code student_attempt}
 * ({@code specs/14-P5-Delivery-V1.5.md §8, §13}). It is independent of {@link AttemptStatus}.
 */
public enum SnapshotStatus {
    PENDING,
    PREPARING,
    READY,
    FAILED
}
