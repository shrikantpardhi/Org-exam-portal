/**
 * Delivery module boundary.
 *
 * <p>Owns AssessmentSession, StudentAttempt, AttemptQuestion, AttemptQuestionOption,
 * StudentResponse, and DeliveryPolicy. Responsible for asynchronous snapshot preparation,
 * assessment launch, timer, navigation, autosave with stale-write protection, session recovery,
 * and submission. It is intentionally separate from assessment authoring.
 *
 * <p>Start Exam performs no synchronous snapshot generation
 * ({@code specs/06-Architecture-Freeze-V1.5.md §116}). P0 establishes this boundary only; the
 * module is implemented in P5.
 *
 * <p>See {@code specs/06-Architecture-Freeze-V1.5.md §35}.
 */
package com.company.exam.delivery;
