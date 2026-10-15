package com.company.exam.evaluation.service;

import com.company.exam.common.audit.SecurityEventLogger;
import com.company.exam.common.exception.NotFoundException;
import com.company.exam.delivery.api.AttemptEvaluationCatalog;
import com.company.exam.evaluation.dto.EvaluationDtos;
import com.company.exam.evaluation.entity.AnswerKey;
import com.company.exam.evaluation.entity.Evaluation;
import com.company.exam.evaluation.entity.Result;
import com.company.exam.evaluation.entity.ResultStatus;
import com.company.exam.evaluation.entity.Scorecard;
import com.company.exam.evaluation.exception.ResultNotPublishableException;
import com.company.exam.evaluation.exception.ScorecardGenerationFailedException;
import com.company.exam.evaluation.mapper.EvaluationMapper;
import com.company.exam.evaluation.observability.EvaluationMetrics;
import com.company.exam.evaluation.repository.AnswerKeyRepository;
import com.company.exam.evaluation.repository.EvaluationRepository;
import com.company.exam.evaluation.repository.ResultRepository;
import com.company.exam.evaluation.repository.ScorecardRepository;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Scorecard generation and secure access ({@code specs/15-P6-Evaluation-V1.5.md §57-§61, §98, §118, §180}).
 *
 * <p>A scorecard is a presentation artifact, generated outside the scoring transaction. It is
 * business-idempotent per result ({@code UNIQUE(result_id)}), requires a published result, and the
 * stored filesystem reference is never returned to a caller ({@code §58, §60}).
 */
@Service
public class ScorecardService {

    private final ScorecardRepository scorecardRepository;
    private final ResultRepository resultRepository;
    private final EvaluationRepository evaluationRepository;
    private final AnswerKeyRepository answerKeyRepository;
    private final AttemptEvaluationCatalog delivery;
    private final EvaluationMapper evaluationMapper;
    private final EvaluationMetrics metrics;
    private final SecurityEventLogger audit;
    private final String scorecardRoot;

    public ScorecardService(ScorecardRepository scorecardRepository, ResultRepository resultRepository,
            EvaluationRepository evaluationRepository, AnswerKeyRepository answerKeyRepository,
            AttemptEvaluationCatalog delivery, EvaluationMapper evaluationMapper, EvaluationMetrics metrics,
            SecurityEventLogger audit,
            @Value("${app.evaluation.scorecard-root:./data/scorecards}") String scorecardRoot) {
        this.scorecardRepository = scorecardRepository;
        this.resultRepository = resultRepository;
        this.evaluationRepository = evaluationRepository;
        this.answerKeyRepository = answerKeyRepository;
        this.delivery = delivery;
        this.evaluationMapper = evaluationMapper;
        this.metrics = metrics;
        this.audit = audit;
        this.scorecardRoot = scorecardRoot;
    }

    @Transactional
    public EvaluationDtos.ScorecardResponse generate(UUID resultId, UUID actorId) {
        Result result = resultRepository.findById(resultId)
                .orElseThrow(() -> NotFoundException.of("Result", resultId));
        if (result.getResultStatus() != ResultStatus.PUBLISHED) {
            throw new ResultNotPublishableException("A scorecard can only be generated for a published result");
        }
        Scorecard existing = scorecardRepository.findByResultId(resultId).orElse(null);
        if (existing != null) {
            return evaluationMapper.toResponse(existing);
        }

        Evaluation evaluation = evaluationRepository.findById(result.getEvaluationId())
                .orElseThrow(() -> NotFoundException.of("Evaluation", result.getEvaluationId()));
        String reference = writeArtifact(result, evaluation);

        Scorecard scorecard = new Scorecard();
        scorecard.setResultId(resultId);
        scorecard.setGeneratedOn(Instant.now());
        scorecard.setFilePath(reference);
        try {
            Scorecard saved = scorecardRepository.saveAndFlush(scorecard);
            metrics.scorecardGenerated();
            audit.audit(actorId, "GENERATE", "Scorecard", saved.getId(), "Generated scorecard");
            return evaluationMapper.toResponse(saved);
        } catch (DataIntegrityViolationException race) {
            return evaluationMapper.toResponse(scorecardRepository.findByResultId(resultId)
                    .orElseThrow(() -> new ScorecardGenerationFailedException("Scorecard was not persisted", race)));
        }
    }

    @Transactional(readOnly = true)
    public EvaluationDtos.ScorecardResponse get(UUID resultId, UUID userId, boolean staff) {
        Result result = resultRepository.findById(resultId)
                .orElseThrow(() -> NotFoundException.of("Result", resultId));
        Evaluation evaluation = evaluationRepository.findById(result.getEvaluationId())
                .orElseThrow(() -> NotFoundException.of("Evaluation", result.getEvaluationId()));
        if (!staff) {
            if (result.getResultStatus() != ResultStatus.PUBLISHED) {
                throw NotFoundException.of("Scorecard for result", resultId);
            }
            AttemptEvaluationCatalog.AttemptView attempt = delivery.requireAttempt(evaluation.getAttemptId());
            if (!attempt.userId().equals(userId)) {
                throw NotFoundException.of("Scorecard for result", resultId);
            }
        }
        Scorecard scorecard = scorecardRepository.findByResultId(resultId)
                .orElseThrow(() -> NotFoundException.of("Scorecard for result", resultId));
        return evaluationMapper.toResponse(scorecard);
    }

    private String writeArtifact(Result result, Evaluation evaluation) {
        UUID assessmentVersionId = answerKeyRepository.findById(evaluation.getAnswerKeyId())
                .map(AnswerKey::getAssessmentVersionId).orElse(null);
        String html = scorecardHtml(result, evaluation, assessmentVersionId);
        try {
            Path directory = Path.of(scorecardRoot);
            Files.createDirectories(directory);
            Path target = directory.resolve(result.getId() + ".html");
            Files.writeString(target, html, StandardCharsets.UTF_8);
            // A relative, non-revealing storage reference; never an absolute filesystem path.
            return "scorecards/" + result.getId() + ".html";
        } catch (IOException failure) {
            metrics.scorecardGenerationFailed();
            throw new ScorecardGenerationFailedException("Scorecard artifact could not be stored", failure);
        }
    }

    private String scorecardHtml(Result result, Evaluation evaluation, UUID assessmentVersionId) {
        return """
                <!doctype html>
                <html lang="en">
                <head><meta charset="utf-8"><title>Scorecard</title></head>
                <body>
                <h1>Scorecard</h1>
                <dl>
                  <dt>Result</dt><dd>%s</dd>
                  <dt>Attempt</dt><dd>%s</dd>
                  <dt>Assessment version</dt><dd>%s</dd>
                  <dt>Obtained marks</dt><dd>%s</dd>
                  <dt>Percentage</dt><dd>%s</dd>
                  <dt>Rank</dt><dd>%s</dd>
                  <dt>Percentile</dt><dd>%s</dd>
                </dl>
                </body>
                </html>
                """.formatted(result.getId(), evaluation.getAttemptId(), assessmentVersionId,
                result.getObtainedMarks(), result.getPercentage(), result.getRank(), result.getPercentile());
    }
}
