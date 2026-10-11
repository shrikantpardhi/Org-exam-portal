package com.company.exam.delivery.engine;

import com.company.exam.assessment.entity.EngineType;
import java.util.List;
import java.util.UUID;

/**
 * The inputs an {@link ExamEngine} needs to resolve normalized runtime configuration
 * ({@code specs/14-P5-Delivery-V1.5.md §33, §34}).
 *
 * @param engineType               the published engine type
 * @param versionId                the exact published AssessmentVersion id
 * @param mode                     the schedule mode (PRACTICE or EXAM)
 * @param durationMinutes          the published duration, or {@code null} when untimed
 * @param sectionOrder             the section names in snapshot order
 * @param deliveryPolicyAttributes the flat attributes parsed from the referenced delivery policy
 */
public record ExamEngineContext(
        EngineType engineType,
        UUID versionId,
        String mode,
        Integer durationMinutes,
        List<String> sectionOrder,
        DeliveryPolicyAttributes deliveryPolicyAttributes) {
}
