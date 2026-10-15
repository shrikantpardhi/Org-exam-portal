package com.company.exam.analytics.service;

import java.util.UUID;

/**
 * Cache key construction ({@code specs/17-P8-Analytics-V1.5.md §62}).
 *
 * <p>Each key includes the relevant scope (organization, user, assessment, topic, version where
 * applicable) so a cached aggregate can never be served for the wrong scope.
 */
final class AnalyticsCacheKeys {

    private static final String PREFIX = "analytics:";

    private AnalyticsCacheKeys() {
    }

    static String student(UUID userId) {
        return PREFIX + "student:" + userId;
    }

    static String assessment(UUID assessmentId) {
        return PREFIX + "assessment:" + assessmentId;
    }

    static String question(UUID questionId) {
        return PREFIX + "question:" + questionId;
    }

    static String topic(UUID topicId) {
        return PREFIX + "topic:" + topicId;
    }

    static String organization(UUID organizationId) {
        return PREFIX + "organization:" + organizationId;
    }
}
