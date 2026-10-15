package com.company.exam.evaluation.service;

import com.company.exam.common.audit.SecurityEventLogger;
import com.company.exam.delivery.api.AttemptEvaluationCatalog;
import com.company.exam.evaluation.dto.EvaluationDtos;
import com.company.exam.evaluation.entity.AnswerKey;
import com.company.exam.evaluation.entity.Evaluation;
import com.company.exam.evaluation.entity.EvaluationStatus;
import com.company.exam.evaluation.entity.Result;
import com.company.exam.evaluation.entity.ResultStatus;
import com.company.exam.evaluation.exception.ReEvaluationNotAllowedException;
import com.company.exam.evaluation.observability.EvaluationMetrics;
import com.company.exam.evaluation.repository.EvaluationRepository;
import com.company.exam.evaluation.repository.ResultRepository;
import com.company.exam.evaluation.scoring.EvaluationOutcome;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Controlled re-evaluation ({@code specs/15-P6-Evaluation-V1.5.md §46-§52, §56}).
 *
 * <p>Re-evaluation never mutates the previous evaluation. It supersedes version N and creates version
 * N+1, supersedes the previous result, and creates a fresh CALCULATED result. The exact historical
 * chain remains queryable ({@code §47, §183}).
 *
 * <p>The partial unique index {@code UNIQUE(attempt_id) WHERE superseded_by_id IS NULL} permits only
 * one current row, so the previous version is first released from the current slot (a transient,
 * never-committed self-reference), then version N+1 is inserted, then the supersede pointer is
 * finalised. The assessment version's ranks are recomputed because its population changed
 * ({@code §105}).
 */
@Service
public class ReEvaluationService {

    private final EvaluationEngine engine;
    private final EvaluationRepository evaluationRepository;
    private final ResultRepository resultRepository;
    private final RankingService rankingService;
    private final SecurityEventLogger audit;
    private final EvaluationMetrics metrics;

    public ReEvaluationService(EvaluationEngine engine, EvaluationRepository evaluationRepository,
            ResultRepository resultRepository, RankingService rankingService, SecurityEventLogger audit,
            EvaluationMetrics metrics) {
        this.engine = engine;
        this.evaluationRepository = evaluationRepository;
        this.resultRepository = resultRepository;
        this.rankingService = rankingService;
        this.audit = audit;
        this.metrics = metrics;
    }

    @Transactional
    public EvaluationDtos.ReEvaluationResponse reEvaluate(UUID attemptId, UUID answerKeyId, String reason,
            UUID actorId) {
        metrics.reevaluationRequested();
        AttemptEvaluationCatalog.AttemptView attempt = engine.requireSubmittableAttempt(attemptId);

        Evaluation previous = evaluationRepository.findByAttemptIdAndSupersededByIdIsNull(attemptId)
                .orElseThrow(() -> new ReEvaluationNotAllowedException(
                        "This attempt has no current evaluation to re-evaluate"));
        AnswerKey key = engine.requireEvaluableKey(answerKeyId, attempt.assessmentVersionId());
        audit.audit(actorId, "RE_EVALUATE_REQUESTED", "Evaluation", previous.getId(), "Re-evaluation requested");

        EvaluationOutcome outcome = engine.calculate(attempt, key.getId());
        Instant now = Instant.now();
        String supersedeReason = reason == null || reason.isBlank() ? "Re-evaluation" : reason.trim();

        previous.setSupersededById(previous.getId());
        previous.setSupersededOn(now);
        previous.setSupersededReason(supersedeReason);
        previous.setStatus(EvaluationStatus.SUPERSEDED);
        evaluationRepository.saveAndFlush(previous);

        Evaluation fresh = new Evaluation();
        fresh.setAttemptId(attempt.attemptId());
        fresh.setAnswerKeyId(key.getId());
        fresh.setVersion(previous.getVersion() + 1);
        fresh.setEvaluatedOn(now);
        fresh.setStatus(EvaluationStatus.COMPLETED);
        Evaluation saved = evaluationRepository.saveAndFlush(fresh);

        previous.setSupersededById(saved.getId());
        evaluationRepository.saveAndFlush(previous);

        Result previousResult = resultRepository.findByEvaluationId(previous.getId()).orElse(null);
        if (previousResult != null) {
            previousResult.setResultStatus(ResultStatus.SUPERSEDED);
            previousResult.setSupersededOn(now);
            resultRepository.saveAndFlush(previousResult);
        }
        Result newResult = engine.persistResult(saved, outcome);

        rankingService.recompute(attempt.assessmentVersionId());

        metrics.reevaluationSucceeded();
        audit.audit(actorId, "RE_EVALUATE", "Evaluation", saved.getId(),
                "Re-evaluated attempt, superseding version " + previous.getVersion());
        return new EvaluationDtos.ReEvaluationResponse(attemptId, saved.getId(), newResult.getId(), previous.getId(),
                saved.getVersion());
    }
}
