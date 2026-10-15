package com.company.exam.delivery.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * The explicit Delivery application contract consumed by Evaluation
 * ({@code specs/15-P6-Evaluation-V1.5.md §9, §64, §65}).
 *
 * <p>Evaluation reads the submitted attempt, its immutable snapshot, and the persisted responses
 * through this contract. It never reaches into Delivery repositories directly, never mutates the
 * snapshot, and never depends on the frontend submission payload.
 */
public interface AttemptEvaluationCatalog {

    /** The attempt identity and lifecycle state, read-only. */
    record AttemptView(UUID attemptId, UUID userId, UUID assessmentVersionId, UUID assessmentScheduleId,
            String attemptStatus, String snapshotStatus) {
    }

    /** One immutable snapshot question. */
    record SnapshotQuestionView(UUID questionVersionId, Integer displayOrder, String sectionName,
            BigDecimal marks, BigDecimal negativeMarks) {
    }

    /** One persisted student response. A {@code null} or blank {@code response} is unanswered. */
    record ResponseView(UUID questionVersionId, String response) {
    }

    /** Returns the attempt, or throws a controlled error if it does not exist. */
    AttemptView requireAttempt(UUID attemptId);

    /** Returns the attempts with the given ids (bounded batch read). */
    List<AttemptView> attempts(java.util.Collection<UUID> attemptIds);

    /** Returns the ordered immutable snapshot questions of the attempt. */
    List<SnapshotQuestionView> snapshot(UUID attemptId);

    /** Returns every persisted response of the attempt. */
    List<ResponseView> responses(UUID attemptId);

    /** The attempt ids owned by a user, newest first (used to bound the staff result list). */
    List<UUID> attemptIdsForUser(UUID userId);
}
