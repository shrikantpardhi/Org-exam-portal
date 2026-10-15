package com.company.exam.delivery.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * The Delivery application contract consumed by Analytics
 * ({@code specs/17-P8-Analytics-V1.5.md §12, §13, §29, §44, §45, §48}).
 *
 * <p>Analytics reads completed attempts, their immutable snapshot (question identity, section, marks)
 * and the persisted responses (answered state and persisted question time) through this contract. It
 * never reaches into Delivery repositories, never mutates runtime state, and never treats client
 * timing as authoritative ({@code §49}).
 */
public interface AttemptAnalyticsCatalog {

    /** One completed attempt row. */
    record AttemptRow(UUID attemptId, UUID userId, UUID assessmentVersionId, String attemptMode,
            String attemptStatus, Instant startedOn) {
    }

    /** One immutable snapshot question. */
    record SnapshotRow(UUID attemptId, UUID questionVersionId, String sectionName, BigDecimal marks) {
    }

    /** One persisted response; {@code answered} is false for a null/blank response. */
    record ResponseRow(UUID attemptId, UUID questionVersionId, Integer timeSpentSeconds, boolean answered) {
    }

    /** The snapshot/answered population of a question version, used to derive the skip rate ({@code §33}). */
    record QuestionPopulation(UUID questionVersionId, long snapshotCount, long answeredCount) {
    }

    /** Attempts that reached a completed state ({@code SUBMITTED} or {@code EVALUATED}). */
    List<AttemptRow> completedAttempts();

    /** Every attempt of the given users, regardless of state. */
    List<AttemptRow> attemptsForUsers(Collection<UUID> userIds);

    /** Every attempt bound to the given assessment versions, regardless of state. */
    List<AttemptRow> attemptsForVersions(Collection<UUID> assessmentVersionIds);

    /** The attempts with the given ids. */
    List<AttemptRow> attemptsByIds(Collection<UUID> attemptIds);

    /** Immutable snapshot questions for a bounded attempt set. */
    List<SnapshotRow> snapshots(Collection<UUID> attemptIds);

    /** Persisted responses for a bounded attempt set. */
    List<ResponseRow> responses(Collection<UUID> attemptIds);

    /** Distinct users with an attempt started since the bound ({@code §44}). */
    List<UUID> activeUserIdsSince(Instant since);

    /** Snapshot and answered populations for the given question versions ({@code §33}). */
    List<QuestionPopulation> questionPopulation(Collection<UUID> questionVersionIds);
}
