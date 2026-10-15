package com.company.exam.evaluation.service;

import com.company.exam.common.audit.SecurityEventLogger;
import com.company.exam.common.exception.NotFoundException;
import com.company.exam.delivery.api.AttemptEvaluationCatalog;
import com.company.exam.evaluation.dto.EvaluationDtos;
import com.company.exam.evaluation.entity.AnswerKey;
import com.company.exam.evaluation.entity.Evaluation;
import com.company.exam.evaluation.entity.Result;
import com.company.exam.evaluation.exception.EvaluationAlreadyExistsException;
import com.company.exam.evaluation.mapper.EvaluationMapper;
import com.company.exam.evaluation.observability.EvaluationMetrics;
import com.company.exam.evaluation.repository.EvaluationRepository;
import com.company.exam.evaluation.repository.ResultRepository;
import com.company.exam.evaluation.scoring.EvaluationOutcome;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Official evaluation orchestration and persistence
 * ({@code specs/15-P6-Evaluation-V1.5.md §41, §44, §45, §89, §92}).
 *
 * <p>Evaluation is evaluate-once per attempt: a repeated request returns the existing current
 * evaluation instead of creating a second row ({@code §44, §45}). A true concurrent race is resolved
 * deterministically by the database's partial unique index and surfaced as a conflict
 * ({@code §129}).
 */
@Service
public class EvaluationService {

    private final EvaluationEngine engine;
    private final EvaluationRepository evaluationRepository;
    private final ResultRepository resultRepository;
    private final EvaluationMapper mapper;
    private final SecurityEventLogger audit;
    private final EvaluationMetrics metrics;

    public EvaluationService(EvaluationEngine engine, EvaluationRepository evaluationRepository,
            ResultRepository resultRepository, EvaluationMapper mapper, SecurityEventLogger audit,
            EvaluationMetrics metrics) {
        this.engine = engine;
        this.evaluationRepository = evaluationRepository;
        this.resultRepository = resultRepository;
        this.mapper = mapper;
        this.audit = audit;
        this.metrics = metrics;
    }

    @Transactional
    public EvaluationDtos.EvaluationResponse evaluate(UUID attemptId, UUID answerKeyId, UUID actorId) {
        metrics.evaluationRequested();
        AttemptEvaluationCatalog.AttemptView attempt = engine.requireSubmittableAttempt(attemptId);

        Optional<Evaluation> existing = evaluationRepository.findByAttemptIdAndSupersededByIdIsNull(attemptId);
        if (existing.isPresent()) {
            return response(existing.get());
        }

        AnswerKey key = engine.requireEvaluableKey(answerKeyId, attempt.assessmentVersionId());
        EvaluationOutcome outcome = engine.calculate(attempt, key.getId());
        try {
            Evaluation evaluation = engine.persistEvaluation(attempt, key.getId(), outcome, 1);
            Result result = engine.persistResult(evaluation, outcome);
            metrics.evaluationSucceeded();
            audit.audit(actorId, "EVALUATE", "Evaluation", evaluation.getId(),
                    "Evaluated attempt against answer key version " + key.getVersion());
            return mapper.toResponse(evaluation, result);
        } catch (DataIntegrityViolationException race) {
            metrics.evaluationFailed();
            throw new EvaluationAlreadyExistsException("This attempt already has a current evaluation");
        }
    }

    @Transactional(readOnly = true)
    public EvaluationDtos.EvaluationResponse get(UUID evaluationId) {
        Evaluation evaluation = evaluationRepository.findById(evaluationId)
                .orElseThrow(() -> NotFoundException.of("Evaluation", evaluationId));
        return response(evaluation);
    }

    /** All evaluation versions for an attempt, newest first, including superseded metadata ({@code §50}). */
    @Transactional(readOnly = true)
    public List<EvaluationDtos.EvaluationResponse> history(UUID attemptId) {
        return evaluationRepository.findByAttemptIdOrderByVersionDesc(attemptId).stream()
                .map(this::response)
                .toList();
    }

    private EvaluationDtos.EvaluationResponse response(Evaluation evaluation) {
        Result result = resultRepository.findByEvaluationId(evaluation.getId()).orElse(null);
        return mapper.toResponse(evaluation, result);
    }
}
