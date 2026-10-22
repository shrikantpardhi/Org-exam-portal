package com.company.exam.delivery.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * The Delivery read contract consumed by Reporting
 * ({@code specs/22-P13-Reporting-V1.5.md §10, §34, §39, §44, §47}).
 *
 * <p>Reporting reads attempt state through this contract and never through the Delivery
 * repositories, and never exposes answer keys or correctness ({@code §39}). Fields are the
 * report-safe projection of an attempt: identity, mode, state, timing and the published total score.
 *
 * <p>Every method is read-only and bounded by an explicit subject ({@code §7.3, §7.6}).
 */
public interface ReportingDeliveryCatalog {

    /** One report-safe attempt row (§39, §44). */
    record AttemptRow(UUID attemptId, UUID userId, UUID assessmentVersionId, UUID assessmentScheduleId,
            String mode, String status, Instant startedOn, Instant submittedOn, BigDecimal totalScore) {
    }

    /** The attempts of one student, newest first, bounded by {@code limit} (§39). */
    List<AttemptRow> attemptsForUser(UUID userId, Instant from, Instant to, int limit);

    /** The attempts of a bounded set of assessment versions, bounded by {@code limit} (§44). */
    List<AttemptRow> attemptsForVersions(Collection<UUID> assessmentVersionIds, Instant from, Instant to,
            int limit);

    /** The attempts linked to one schedule, bounded by {@code limit} (§47). */
    List<AttemptRow> attemptsForSchedule(UUID assessmentScheduleId, int limit);
}
