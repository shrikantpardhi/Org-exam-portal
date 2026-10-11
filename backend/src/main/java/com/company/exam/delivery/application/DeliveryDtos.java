package com.company.exam.delivery.application;

import com.company.exam.assessment.api.AssessmentDefinitionCatalog;
import com.company.exam.delivery.engine.DeliveryConfiguration;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Request and response payloads for the Delivery runtime API ({@code §28, §38-§46, §63}). */
public final class DeliveryDtos {

    private DeliveryDtos() {
    }

    /** The timer state derived from the server-authoritative deadline ({@code §54, §57}). */
    public record TimerView(String mode, Integer durationSeconds, Instant deadline) {
    }

    public record StartResponse(UUID sessionId, UUID attemptId, UUID assessmentVersionId, String mode,
            Instant startedOn, Instant serverTime, TimerView timer, DeliveryConfiguration deliveryConfiguration) {
    }

    public record SessionResponse(UUID sessionId, UUID attemptId, UUID scheduleId, String sessionStatus,
            Instant startedOn, Instant lastActivityOn, Instant endedOn, Instant serverTime, TimerView timer) {
    }

    public record ProgressSummary(long totalQuestions, long answered, long visited, long markedForReview) {
    }

    public record AttemptResponse(UUID attemptId, UUID assessmentVersionId, UUID scheduleId, String attemptMode,
            String attemptStatus, String snapshotStatus, Instant startedOn, Instant submittedOn, Instant serverTime,
            TimerView timer, DeliveryConfiguration deliveryConfiguration, ProgressSummary progress) {
    }

    public record OptionView(UUID optionId, String optionKey, Integer displayOrder, String content,
            String renderedContent) {
    }

    public record AttemptQuestionView(UUID attemptQuestionId, UUID questionVersionId, Integer displayOrder,
            String sectionName, BigDecimal marks, BigDecimal negativeMarks, boolean requiresLatexParsing,
            String contentFormat, String content, String renderedContent, List<OptionView> options) {
    }

    public record ResponseView(UUID attemptId, UUID questionVersionId, String response, Integer timeSpentSeconds,
            boolean markedForReview, boolean visited, String etag) {
    }

    public record ResponseSaveRequest(String response, Integer timeSpentSeconds, Boolean markedForReview,
            Boolean visited) {
    }

    public record SubmitRequest(Boolean confirmed) {
    }

    public record SubmitResponse(UUID attemptId, String status, Instant submittedOn) {
    }

    public record PrepareResponse(UUID jobId, String status, UUID scheduleId) {
    }
}
