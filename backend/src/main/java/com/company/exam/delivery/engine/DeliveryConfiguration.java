package com.company.exam.delivery.engine;

import java.util.List;

/**
 * The normalized, declarative runtime configuration returned by Start Exam and recovery
 * ({@code specs/14-P5-Delivery-V1.5.md §34}).
 *
 * <p>It is an experience and enforcement contract, never executable configuration. It never
 * contains {@code renderScript}, {@code javascript}, {@code customComponent}, {@code eval},
 * {@code expression}, {@code databaseQuery}, or {@code arbitraryUrl} ({@code §35}).
 */
public record DeliveryConfiguration(
        Capabilities capabilities,
        NavigationPolicy navigation,
        TimerPolicy timer,
        SubmissionPolicy submission,
        ValidationPolicy validation,
        PresentationPolicy presentation) {

    public record Capabilities(boolean practice, boolean allowClearResponse, boolean allowMarkForReview,
            boolean allowExplanation, boolean allowHint) {
    }

    public record NavigationPolicy(String mode, boolean allowBackNavigation, boolean allowSectionSwitch,
            boolean allowQuestionReview) {
    }

    public record TimerPolicy(String mode, Integer durationSeconds, boolean enforced) {
    }

    public record SubmissionPolicy(boolean autoSubmitOnExpiry, boolean allowManualSubmit) {
    }

    public record ValidationPolicy(boolean requireResponseWithinTemplate, int maxResponseLength,
            int autoSaveIntervalSeconds) {
    }

    public record PresentationPolicy(boolean showPalette, boolean showTimer, boolean requiresLatexParsing,
            String contentFormat, List<String> sectionOrder) {
    }
}
