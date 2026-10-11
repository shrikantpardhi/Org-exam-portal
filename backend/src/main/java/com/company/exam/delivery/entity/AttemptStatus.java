package com.company.exam.delivery.entity;

/** The runtime attempt state machine ({@code specs/14-P5-Delivery-V1.5.md §8, §13}). */
public enum AttemptStatus {
    NOT_STARTED,
    IN_PROGRESS,
    SUBMITTED,
    EVALUATED
}
