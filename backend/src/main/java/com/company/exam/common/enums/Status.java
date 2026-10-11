package com.company.exam.common.enums;

/**
 * Standard entity lifecycle status.
 *
 * <p>Soft delete uses this status rather than physical deletion
 * ({@code specs/04-Database-Schema-V1.5.md §8, §63}).
 */
public enum Status {
    ACTIVE,
    INACTIVE,
    ARCHIVED
}
