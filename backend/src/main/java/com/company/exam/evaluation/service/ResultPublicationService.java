package com.company.exam.evaluation.service;

import com.company.exam.common.audit.CurrentAuditorProvider;
import com.company.exam.common.audit.SecurityEventLogger;
import com.company.exam.common.exception.NotFoundException;
import com.company.exam.common.job.BackgroundJob;
import com.company.exam.common.job.BackgroundJobRepository;
import com.company.exam.common.job.BackgroundJobStatus;
import com.company.exam.common.job.JobTypes;
import com.company.exam.evaluation.dto.EvaluationDtos;
import com.company.exam.evaluation.entity.AnswerKey;
import com.company.exam.evaluation.entity.Evaluation;
import com.company.exam.evaluation.entity.EvaluationStatus;
import com.company.exam.evaluation.entity.Result;
import com.company.exam.evaluation.entity.ResultStatus;
import com.company.exam.evaluation.exception.ResultNotPublishableException;
import com.company.exam.evaluation.mapper.EvaluationMapper;
import com.company.exam.evaluation.observability.EvaluationMetrics;
import com.company.exam.evaluation.repository.AnswerKeyRepository;
import com.company.exam.evaluation.repository.EvaluationRepository;
import com.company.exam.evaluation.repository.ResultRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Result publication ({@code specs/15-P6-Evaluation-V1.5.md §54, §55, §85, §97, §130, §160}).
 *
 * <p>Publication is a short state transition with authorization, state validation, and audit. The
 * score is never recalculated. Ranks and percentiles for the affected AssessmentVersion are
 * recomputed in the same transaction batch, so a published result is never left without a rank
 * ({@code §160}).
 */
@Service
public class ResultPublicationService {

    private final ResultRepository resultRepository;
    private final EvaluationRepository evaluationRepository;
    private final AnswerKeyRepository answerKeyRepository;
    private final RankingService rankingService;
    private final EvaluationMapper mapper;
    private final SecurityEventLogger audit;
    private final EvaluationMetrics metrics;
    private final BackgroundJobRepository jobRepository;
    private final com.company.exam.delivery.api.AttemptEvaluationCatalog delivery;
    private final com.company.exam.assessment.api.AssessmentDefinitionCatalog assessment;
    private final org.springframework.context.ApplicationEventPublisher events;

    public ResultPublicationService(ResultRepository resultRepository, EvaluationRepository evaluationRepository,
            AnswerKeyRepository answerKeyRepository, RankingService rankingService, EvaluationMapper mapper,
            SecurityEventLogger audit, EvaluationMetrics metrics, BackgroundJobRepository jobRepository,
            com.company.exam.delivery.api.AttemptEvaluationCatalog delivery,
            com.company.exam.assessment.api.AssessmentDefinitionCatalog assessment,
            org.springframework.context.ApplicationEventPublisher events) {
        this.resultRepository = resultRepository;
        this.evaluationRepository = evaluationRepository;
        this.answerKeyRepository = answerKeyRepository;
        this.rankingService = rankingService;
        this.mapper = mapper;
        this.audit = audit;
        this.metrics = metrics;
        this.jobRepository = jobRepository;
        this.delivery = delivery;
        this.assessment = assessment;
        this.events = events;
    }

    @Transactional
    public EvaluationDtos.ResultResponse publish(UUID resultId, UUID actorId) {
        Result result = resultRepository.findById(resultId)
                .orElseThrow(() -> NotFoundException.of("Result", resultId));
        Evaluation evaluation = evaluationRepository.findById(result.getEvaluationId())
                .orElseThrow(() -> NotFoundException.of("Evaluation", result.getEvaluationId()));

        if (result.getResultStatus() == ResultStatus.PUBLISHED) {
            return describe(result, evaluation);
        }
        if (result.getResultStatus() == ResultStatus.SUPERSEDED) {
            throw new ResultNotPublishableException("A superseded result cannot be published");
        }
        if (evaluation.getStatus() != EvaluationStatus.COMPLETED) {
            throw new ResultNotPublishableException("The evaluation is not complete");
        }

        result.setResultStatus(ResultStatus.PUBLISHED);
        resultRepository.saveAndFlush(result);

        UUID assessmentVersionId = answerKeyRepository.findById(evaluation.getAnswerKeyId())
                .map(AnswerKey::getAssessmentVersionId)
                .orElseThrow(() -> NotFoundException.of("Answer key", evaluation.getAnswerKeyId()));
        rankingService.recompute(assessmentVersionId);
        enqueueAnalyticsRefresh(evaluation.getAttemptId());
        publishResultEvent(evaluation.getAttemptId(), assessmentVersionId);

        metrics.resultPublished();
        audit.audit(actorId, "PUBLISH", "Result", result.getId(), "Published result");

        Result published = resultRepository.findById(resultId).orElseThrow();
        return describe(published, evaluation);
    }

    private EvaluationDtos.ResultResponse describe(Result result, Evaluation evaluation) {
        UUID assessmentVersionId = answerKeyRepository.findById(evaluation.getAnswerKeyId())
                .map(AnswerKey::getAssessmentVersionId)
                .orElse(null);
        return mapper.toResponse(result, evaluation.getAttemptId(), assessmentVersionId);
    }

    /**
     * Signals the derived communication workflow that a result became authoritative
     * ({@code specs/19-P10-Communication-V1.5.md §55}). The event is a shared in-process event;
     * Evaluation does not depend on Communication, and delivery happens after commit.
     */
    private void publishResultEvent(UUID attemptId, UUID assessmentVersionId) {
        try {
            UUID userId = delivery.requireAttempt(attemptId).userId();
            UUID assessmentId = assessment.requireScoringContext(assessmentVersionId).assessmentId();
            events.publishEvent(new com.company.exam.common.events.BusinessEvents.ResultPublishedEvent(attemptId,
                    userId, assessmentId));
        } catch (RuntimeException ignored) {
            // A missing mapping must never fail result publication (communication is derived).
        }
    }

    /**
     * Signals a derived analytics refresh when a result becomes authoritative
     * ({@code specs/17-P8-Analytics-V1.5.md §64}). The signal uses the shared {@code background_job}
     * infrastructure, so Evaluation never depends on the Analytics module.
     */
    private void enqueueAnalyticsRefresh(UUID attemptId) {
        BackgroundJob job = new BackgroundJob();
        job.setJobType(JobTypes.ANALYTICS_REFRESH);
        job.setReferenceType("STUDENT_ATTEMPT");
        job.setReferenceId(attemptId);
        job.setInitiatedBy(CurrentAuditorProvider.SYSTEM_ACTOR_ID);
        job.setStatus(BackgroundJobStatus.QUEUED);
        job.setParameters("{\"attemptId\":\"" + attemptId + "\"}");
        job.setQueuedOn(Instant.now());
        job.setProgressCurrent(0);
        job.setProgressTotal(1);
        job.setAttemptCount(0);
        jobRepository.save(job);
    }
}
