package com.company.exam.delivery.api;

import java.util.Optional;
import java.util.UUID;

/**
 * Delivery's submit-time contract for designating the evaluation answer key
 * ({@code specs/14-P5-Delivery-V1.5.md §67} with {@code specs/15-P6-Evaluation-V1.5.md §25, §26}).
 *
 * <p>When an attempt transitions to {@code SUBMITTED}, the handoff to Evaluation must carry an
 * explicit answer key rather than a bare attempt. This contract asks Evaluation which published
 * {@code AnswerKey} applies to the attempt's exact {@code AssessmentVersion}, so Delivery never
 * reads the {@code answer_key} table directly ({@code 15-P6 §64}). Evaluation owns the resolution
 * rule; Delivery only enqueues the handoff with the resolved id, or skips the handoff when none is
 * published.
 */
public interface SubmitAnswerKeyResolver {

    /**
     * Resolves the published answer key for an assessment version at submission time.
     *
     * <ul>
     *   <li>exactly one published key → that key</li>
     *   <li>multiple published keys → the highest version</li>
     *   <li>none published → {@link Optional#empty()} (the caller must not enqueue a job)</li>
     * </ul>
     *
     * <p>It never defaults to a "current published key" chosen by {@code created_on} or any other
     * implicit rule ({@code 15-P6 §26}).
     */
    Optional<UUID> resolveForSubmit(UUID assessmentVersionId);
}
