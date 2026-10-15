package com.company.exam.analytics.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * The immutable, in-memory source facts for one attempt ({@code specs/17-P8-Analytics-V1.5.md §13, §98-§102}).
 *
 * <p>It is assembled from authoritative transactional data via application contracts and is never
 * persisted; the analytics calculators are pure functions over it.
 */
record AnalyticsFact(UUID attemptId, UUID userId, UUID assessmentId, UUID assessmentVersionId,
        BigDecimal score, List<QuestionFact> questions) {

    /** One evaluated snapshot question occurrence. */
    record QuestionFact(UUID questionVersionId, UUID questionId, UUID topicId, String sectionName,
            String state, BigDecimal awardedMarks, BigDecimal marks, Integer timeSpentSeconds, boolean answered) {

        /** A correct or incorrect outcome counts toward the question/topic denominator ({@code §35}). */
        boolean counted() {
            return "CORRECT".equals(state) || "INCORRECT".equals(state);
        }

        boolean correct() {
            return "CORRECT".equals(state);
        }
    }
}
