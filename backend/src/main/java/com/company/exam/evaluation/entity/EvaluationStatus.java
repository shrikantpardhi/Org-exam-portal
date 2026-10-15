package com.company.exam.evaluation.entity;

/**
 * The controlled {@code evaluation.status} vocabulary ({@code specs/15-P6-Evaluation-V1.5.md §6}).
 */
public enum EvaluationStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    FAILED,
    SUPERSEDED
}
