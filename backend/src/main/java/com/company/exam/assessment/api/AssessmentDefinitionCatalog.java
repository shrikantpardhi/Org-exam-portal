package com.company.exam.assessment.api;

import com.company.exam.assessment.entity.ScheduleMode;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The explicit Assessment application contract consumed by Delivery
 * ({@code specs/14-P5-Delivery-V1.5.md §15, §16}).
 *
 * <p>Delivery materializes its runtime snapshot from the published handoff exposed here. It never
 * edits published P4 artifacts, never resolves a latest QuestionVersion, and never reaches into
 * Assessment repositories directly.
 */
public interface AssessmentDefinitionCatalog {

    /** The published assessment version handoff. {@code published} must be true to be delivered. */
    record PublishedAssessmentVersion(UUID id, UUID assessmentId, UUID blueprintVersionId, Integer durationMinutes,
            String engineType, Integer engineConfigurationVersion, UUID deliveryPolicyId, boolean published) {
    }

    /** The schedule binding a published version to a window and a mode. */
    record ScheduleView(UUID id, UUID assessmentVersionId, ScheduleMode mode, Instant startTime, Instant endTime,
            String status) {
    }

    /**
     * The schedule and the published version it binds, resolved together (GAP-059).
     *
     * <p>Start Exam needs both on every call and neither can change during the launch, so they are
     * read in a single statement rather than one round trip each
     * ({@code specs/14-P5-Delivery-V1.5.md §26}).
     */
    record ScheduleBinding(ScheduleView schedule, PublishedAssessmentVersion version) {
    }

    /** One exact question binding in the published assessment definition. */
    record AssessmentQuestionView(UUID questionVersionId, Integer displayOrder, String sectionName, BigDecimal marks,
            BigDecimal negativeMarks) {
    }

    /**
     * The scoring context Evaluation needs from Assessment
     * ({@code specs/15-P6-Evaluation-V1.5.md §18, §34, §109}).
     */
    record AssessmentScoringView(UUID assessmentVersionId, UUID assessmentId, boolean published,
            Integer totalQuestions, BigDecimal totalMarks) {
    }

    /**
     * Returns the assessment version, or throws a controlled error if it does not exist or is not
     * published.
     */
    PublishedAssessmentVersion requirePublishedVersion(UUID assessmentVersionId);

    /** Returns the schedule, or throws a controlled error if it does not exist. */
    ScheduleView requireSchedule(UUID scheduleId);

    /**
     * Returns the schedule and its published version in one read, throwing the same controlled
     * errors as {@link #requireSchedule} and {@link #requirePublishedVersion}.
     */
    ScheduleBinding requireScheduleBinding(UUID scheduleId);

    /** Returns the ordered exact question bindings of a published assessment version. */
    List<AssessmentQuestionView> questions(UUID assessmentVersionId);

    /**
     * Returns the scoring context (including the published {@code total_marks}) for an assessment
     * version, or throws a controlled error if it does not exist.
     */
    AssessmentScoringView requireScoringContext(UUID assessmentVersionId);

    /** Returns the ids of every version of an assessment ({@code specs/15-P6 §90, §127}). */
    List<UUID> assessmentVersionIds(UUID assessmentId);
}
