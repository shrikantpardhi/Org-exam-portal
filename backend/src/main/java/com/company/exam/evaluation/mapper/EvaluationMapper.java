package com.company.exam.evaluation.mapper;

import com.company.exam.evaluation.dto.EvaluationDtos;
import com.company.exam.evaluation.entity.AnswerKey;
import com.company.exam.evaluation.entity.AnswerKeyDetail;
import com.company.exam.evaluation.entity.Evaluation;
import com.company.exam.evaluation.entity.Result;
import com.company.exam.evaluation.entity.Scorecard;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Maps Evaluation-owned entities to their DTO projections ({@code specs/15-P6-Evaluation-V1.5.md §116-§118}). */
@Component
public class EvaluationMapper {

    public EvaluationDtos.AnswerKeyResponse toResponse(AnswerKey key) {
        return new EvaluationDtos.AnswerKeyResponse(key.getId(), key.getAssessmentVersionId(), key.getVersion(),
                key.isPublished(), key.getPublishedBy(), key.getPublishedOn(), key.getCreatedOn(), key.getUpdatedOn());
    }

    public EvaluationDtos.AnswerKeyDetailResponse toResponse(AnswerKeyDetail detail) {
        return new EvaluationDtos.AnswerKeyDetailResponse(detail.getId(), detail.getAnswerKeyId(),
                detail.getQuestionVersionId(), detail.getCorrectOptionId(), detail.getCorrectAnswerText(),
                detail.getQuestionOutcome() == null ? null : detail.getQuestionOutcome().name(),
                detail.getAwardedMarks(), detail.getAwardedNegativeMarks(), detail.getRemarks());
    }

    public EvaluationDtos.ResultSummary toSummary(Result result) {
        if (result == null) {
            return null;
        }
        return new EvaluationDtos.ResultSummary(result.getId(), result.getObtainedMarks(), result.getPercentage(),
                result.getRank(), result.getPercentile(),
                result.getResultStatus() == null ? null : result.getResultStatus().name());
    }

    public EvaluationDtos.EvaluationResponse toResponse(Evaluation evaluation, Result result) {
        return new EvaluationDtos.EvaluationResponse(evaluation.getId(), evaluation.getAttemptId(),
                evaluation.getAnswerKeyId(), evaluation.getVersion(),
                evaluation.getStatus() == null ? null : evaluation.getStatus().name(), evaluation.getEvaluatedOn(),
                evaluation.getSupersededById(), evaluation.getSupersededOn(), evaluation.getSupersededReason(),
                toSummary(result));
    }

    public EvaluationDtos.ResultResponse toResponse(Result result, UUID attemptId, UUID assessmentVersionId) {
        return new EvaluationDtos.ResultResponse(result.getId(), result.getEvaluationId(), attemptId,
                assessmentVersionId, result.getObtainedMarks(), result.getPercentage(), result.getRank(),
                result.getPercentile(), result.getResultStatus() == null ? null : result.getResultStatus().name(),
                result.getSupersededOn());
    }

    public EvaluationDtos.ScorecardResponse toResponse(Scorecard scorecard) {
        return new EvaluationDtos.ScorecardResponse(scorecard.getId(), scorecard.getResultId(),
                scorecard.getStatus() == null ? null : scorecard.getStatus().name(), scorecard.getGeneratedOn(),
                "/api/v1/results/" + scorecard.getResultId() + "/scorecard");
    }
}
