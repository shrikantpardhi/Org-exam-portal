package com.company.exam.evaluation.service;

import com.company.exam.common.exception.ApiException;
import com.company.exam.delivery.api.AttemptEvaluationCatalog;
import com.company.exam.evaluation.api.AnalyticsOutcomeCatalog;
import com.company.exam.evaluation.entity.Evaluation;
import com.company.exam.evaluation.entity.Result;
import com.company.exam.evaluation.entity.ResultStatus;
import com.company.exam.evaluation.repository.EvaluationRepository;
import com.company.exam.evaluation.repository.ResultRepository;
import com.company.exam.evaluation.scoring.EvaluationOutcome;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Default {@link AnalyticsOutcomeCatalog} ({@code specs/17-P8-Analytics-V1.5.md §13, §25, §31, §96}).
 *
 * <p>Official scores come from the persisted Result. Per-question outcomes are produced by the
 * <em>existing</em> P6 evaluator ({@link EvaluationEngine}) over the authoritative answer key of the
 * attempt's current evaluation — Analytics does not implement a second scoring engine
 * ({@code §1966}). P6 does not persist per-question outcomes (gap GAP-079), so they are derived here
 * deterministically and never cached as authoritative.
 */
@Service
public class AnalyticsOutcomeCatalogService implements AnalyticsOutcomeCatalog {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsOutcomeCatalogService.class);

    private final EvaluationRepository evaluationRepository;
    private final ResultRepository resultRepository;
    private final EvaluationEngine evaluationEngine;
    private final AttemptEvaluationCatalog delivery;

    public AnalyticsOutcomeCatalogService(EvaluationRepository evaluationRepository,
            ResultRepository resultRepository, EvaluationEngine evaluationEngine,
            AttemptEvaluationCatalog delivery) {
        this.evaluationRepository = evaluationRepository;
        this.resultRepository = resultRepository;
        this.evaluationEngine = evaluationEngine;
        this.delivery = delivery;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ScoreRow> currentScores(Collection<UUID> attemptIds) {
        if (attemptIds.isEmpty()) {
            return List.of();
        }
        List<Evaluation> current = evaluationRepository.findByAttemptIdInAndSupersededByIdIsNull(attemptIds);
        if (current.isEmpty()) {
            return List.of();
        }
        Map<UUID, Evaluation> evaluationById = current.stream()
                .collect(Collectors.toMap(Evaluation::getId, Function.identity(), (a, b) -> a));
        List<Result> results = resultRepository.findByEvaluationIdIn(evaluationById.keySet());
        return results.stream()
                .filter(result -> result.getResultStatus() != ResultStatus.SUPERSEDED)
                .map(result -> {
                    Evaluation evaluation = evaluationById.get(result.getEvaluationId());
                    return new ScoreRow(evaluation.getAttemptId(), result.getObtainedMarks(),
                            result.getPercentage(),
                            result.getResultStatus() == null ? null : result.getResultStatus().name());
                })
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<QuestionOutcomeView> questionOutcomes(Collection<UUID> attemptIds) {
        if (attemptIds.isEmpty()) {
            return List.of();
        }
        List<Evaluation> current = evaluationRepository.findByAttemptIdInAndSupersededByIdIsNull(attemptIds);
        List<QuestionOutcomeView> outcomes = new java.util.ArrayList<>();
        for (Evaluation evaluation : current) {
            try {
                AttemptEvaluationCatalog.AttemptView attempt = delivery.requireAttempt(evaluation.getAttemptId());
                EvaluationOutcome outcome = evaluationEngine.calculate(attempt, evaluation.getAnswerKeyId());
                outcome.questionOutcomes().forEach(question -> outcomes.add(new QuestionOutcomeView(
                        evaluation.getAttemptId(), question.questionVersionId(), question.state().name(),
                        question.awardedMarks())));
            } catch (ApiException | IllegalStateException skip) {
                // A single non-evaluable attempt must not fail the whole bounded aggregation (§67).
                log.warn("Skipping question outcomes for attemptId={}: {}", evaluation.getAttemptId(),
                        skip.getMessage());
            }
        }
        return outcomes;
    }
}
