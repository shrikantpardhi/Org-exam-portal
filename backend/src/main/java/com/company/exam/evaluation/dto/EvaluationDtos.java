package com.company.exam.evaluation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Request and response payloads for the Evaluation API
 * ({@code specs/15-P6-Evaluation-V1.5.md §30, §48, §88-§91, §116-§118}).
 *
 * <p>Write DTOs never expose server-controlled audit values, and no DTO carries the private
 * {@code scorecard.file_path} ({@code §58}). Student-safe and staff projections are kept separate so
 * answer-key and evaluation internals never leak ({@code §87, §116}).
 */
public final class EvaluationDtos {

    private EvaluationDtos() {
    }

    // ---------------------------------------------------------------- Answer keys

    public record AnswerKeyCreateRequest(@NotNull UUID assessmentVersionId) {
    }

    public record AnswerKeyResponse(UUID id, UUID assessmentVersionId, Integer version, boolean published,
            UUID publishedBy, Instant publishedOn, Instant createdOn, Instant updatedOn) {
    }

    public record AnswerKeyDetailRequest(
            @NotNull UUID questionVersionId,
            UUID correctOptionId,
            List<UUID> correctOptionIds,
            @Size(max = 10000) String correctAnswerText,
            @NotBlank String questionOutcome,
            @PositiveOrZero BigDecimal awardedMarks,
            @PositiveOrZero BigDecimal awardedNegativeMarks,
            @Size(max = 2000) String remarks) {
    }

    public record AnswerKeyDetailResponse(UUID id, UUID answerKeyId, UUID questionVersionId, UUID correctOptionId,
            List<UUID> correctOptionIds, String correctAnswerText, String questionOutcome, BigDecimal awardedMarks,
            BigDecimal awardedNegativeMarks, String remarks) {
    }

    // ---------------------------------------------------------------- Evaluation

    public record EvaluateRequest(@NotNull UUID answerKeyId) {
    }

    public record ReEvaluateRequest(@NotNull UUID answerKeyId, @Size(max = 2000) String reason) {
    }

    /** The official score summary projected from a Result ({@code §116}). */
    public record ResultSummary(UUID id, BigDecimal obtainedMarks, BigDecimal percentage, Integer rank,
            BigDecimal percentile, String resultStatus) {
    }

    public record EvaluationResponse(UUID id, UUID attemptId, UUID answerKeyId, Integer version, String status,
            Instant evaluatedOn, UUID supersededById, Instant supersededOn, String supersededReason,
            ResultSummary result) {
    }

    public record ReEvaluationResponse(UUID attemptId, UUID newEvaluationId, UUID newResultId,
            UUID previousEvaluationId, Integer newVersion) {
    }

    // ---------------------------------------------------------------- Results

    public record ResultResponse(UUID id, UUID evaluationId, UUID attemptId, UUID assessmentVersionId,
            BigDecimal obtainedMarks, BigDecimal percentage, Integer rank, BigDecimal percentile,
            String resultStatus, Instant supersededOn) {
    }

    public record RankingsEntry(Integer rank, UUID userId, String displayName, BigDecimal obtainedMarks,
            BigDecimal percentile) {
    }

    // ---------------------------------------------------------------- Scorecard

    public record ScorecardResponse(UUID scorecardId, UUID resultId, String status, Instant generatedOn,
            String secureDownloadReference) {
    }
}
