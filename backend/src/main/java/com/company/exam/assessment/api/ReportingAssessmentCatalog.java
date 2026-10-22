package com.company.exam.assessment.api;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * The Assessment read contract consumed by Reporting
 * ({@code specs/22-P13-Reporting-V1.5.md §10, §43-§47}).
 *
 * <p>Reporting reads assessment identity, published versions and schedules through this contract and
 * never through Assessment repositories ({@code §10}). Schedule rows carry the raw window and mode;
 * business meaning is not inferred beyond them ({@code §47}).
 *
 * <p>Every method is bounded by {@code limit} (§7.6) and read-only (§7.3).
 */
public interface ReportingAssessmentCatalog {

    /** One logical assessment with its lifecycle state (§43). */
    record AssessmentRow(UUID assessmentId, String code, String name, String status) {
    }

    /** One assessment version and whether it is published (§43). */
    record VersionRow(UUID versionId, UUID assessmentId, Integer version, Integer totalQuestions,
            Integer durationMinutes, boolean published) {
    }

    /** One schedule binding a published version to a window (§47). */
    record ScheduleRow(UUID scheduleId, UUID assessmentVersionId, String mode, Instant startTime,
            Instant endTime, String status) {
    }

    /** Assessments, bounded by {@code limit}. */
    List<AssessmentRow> assessments(int limit);

    /** The versions of the given logical assessments. */
    List<VersionRow> versionsOf(Collection<UUID> assessmentIds);

    /** Schedules for the given assessment versions. */
    List<ScheduleRow> schedulesForVersions(Collection<UUID> assessmentVersionIds);
}
