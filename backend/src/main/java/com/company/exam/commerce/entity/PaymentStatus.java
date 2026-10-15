package com.company.exam.commerce.entity;

/**
 * The frozen {@code payment_record.payment_status} and {@code purchase.payment_status} vocabulary
 * ({@code specs/18-P9-Commerce-V1.5.md §12, §18.1}).
 *
 * <p>Provider-specific lifecycle values are normalized at the adapter boundary and never stored.
 */
public enum PaymentStatus {
    PENDING,
    SUCCESS,
    FAILED,
    REFUNDED
}
