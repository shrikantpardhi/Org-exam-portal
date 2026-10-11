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

    /** One exact question binding in the published assessment definition. */
    record AssessmentQuestionView(UUID questionVersionId, Integer displayOrder, String sectionName, BigDecimal marks,
            BigDecimal negativeMarks) {
    }

    /**
     * Returns the assessment version, or throws a controlled error if it does not exist or is not
     * published.
     */
    PublishedAssessmentVersion requirePublishedVersion(UUID assessmentVersionId);

    /** Returns the schedule, or throws a controlled error if it does not exist. */
    ScheduleView requireSchedule(UUID scheduleId);

    /** Returns the ordered exact question bindings of a published assessment version. */
    List<AssessmentQuestionView> questions(UUID assessmentVersionId);
}
