package com.company.exam.evaluation.service;

import com.company.exam.assessment.api.AssessmentDefinitionCatalog;
import com.company.exam.common.exception.NotFoundException;
import com.company.exam.content.api.ContentCatalog;
import com.company.exam.delivery.api.AttemptEvaluationCatalog;
import com.company.exam.evaluation.entity.AnswerKey;
import com.company.exam.evaluation.entity.AnswerKeyDetail;
import com.company.exam.evaluation.entity.Evaluation;
import com.company.exam.evaluation.entity.EvaluationStatus;
import com.company.exam.evaluation.entity.Result;
import com.company.exam.evaluation.entity.ResultStatus;
import com.company.exam.evaluation.exception.AnswerKeyIncompleteException;
import com.company.exam.evaluation.exception.AnswerKeyNotForAssessmentException;
import com.company.exam.evaluation.exception.AnswerKeyNotPublishedException;
import com.company.exam.evaluation.exception.AttemptNotSubmittedException;
import com.company.exam.evaluation.exception.EvaluationNotAllowedException;
import com.company.exam.evaluation.repository.AnswerKeyDetailRepository;
import com.company.exam.evaluation.repository.AnswerKeyRepository;
import com.company.exam.evaluation.repository.EvaluationRepository;
import com.company.exam.evaluation.repository.ResultRepository;
import com.company.exam.evaluation.scoring.EvaluationCalculator;
import com.company.exam.evaluation.scoring.EvaluationContext;
import com.company.exam.evaluation.scoring.EvaluationOutcome;
import com.company.exam.evaluation.scoring.QuestionEvaluationContext;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * The evaluation orchestration and persistence engine
 * ({@code specs/15-P6-Evaluation-V1.5.md §40, §41, §64, §65, §69, §126}).
 *
 * <p>It loads the exact immutable snapshot, the persisted responses, the content identity of the
 * evaluable questions, and the authoritative answer-key details in a bounded set of reads, then
 * delegates the pure arithmetic to {@link EvaluationCalculator}. It runs inside the caller's
 * transaction and never touches another module's repositories.
 */
@Component
public class EvaluationEngine {

    private static final Set<String> OPTION_TEMPLATES = Set.of("SINGLE_CHOICE", "MULTIPLE_CHOICE", "TRUE_FALSE");
    private static final String SUBMITTED = "SUBMITTED";
    private static final String SNAPSHOT_READY = "READY";

    private final AttemptEvaluationCatalog delivery;
    private final AssessmentDefinitionCatalog assessment;
    private final ContentCatalog content;
    private final AnswerKeyRepository answerKeyRepository;
    private final AnswerKeyDetailRepository detailRepository;
    private final EvaluationRepository evaluationRepository;
    private final ResultRepository resultRepository;
    private final EvaluationCalculator calculator;

    public EvaluationEngine(AttemptEvaluationCatalog delivery, AssessmentDefinitionCatalog assessment,
            ContentCatalog content, AnswerKeyRepository answerKeyRepository,
            AnswerKeyDetailRepository detailRepository, EvaluationRepository evaluationRepository,
            ResultRepository resultRepository, EvaluationCalculator calculator) {
        this.delivery = delivery;
        this.assessment = assessment;
        this.content = content;
        this.answerKeyRepository = answerKeyRepository;
        this.detailRepository = detailRepository;
        this.evaluationRepository = evaluationRepository;
        this.resultRepository = resultRepository;
        this.calculator = calculator;
    }

    /** Loads an attempt and enforces the SUBMITTED + READY preconditions ({@code §42, §43}). */
    public AttemptEvaluationCatalog.AttemptView requireSubmittableAttempt(UUID attemptId) {
        AttemptEvaluationCatalog.AttemptView attempt = delivery.requireAttempt(attemptId);
        if (!SUBMITTED.equals(attempt.attemptStatus())) {
            throw new AttemptNotSubmittedException("Only a SUBMITTED attempt can be evaluated");
        }
        if (!SNAPSHOT_READY.equals(attempt.snapshotStatus())) {
            throw new EvaluationNotAllowedException("The attempt snapshot is not READY");
        }
        return attempt;
    }

    /** Loads an explicit, published answer key bound to the attempt's assessment version ({@code §25, §26}). */
    public AnswerKey requireEvaluableKey(UUID answerKeyId, UUID assessmentVersionId) {
        AnswerKey key = answerKeyRepository.findById(answerKeyId)
                .orElseThrow(() -> NotFoundException.of("Answer key", answerKeyId));
        if (!key.isPublished()) {
            throw new AnswerKeyNotPublishedException("The selected answer key is not published");
        }
        if (!key.getAssessmentVersionId().equals(assessmentVersionId)) {
            throw new AnswerKeyNotForAssessmentException(
                    "The answer key does not belong to the attempt's assessment version");
        }
        return key;
    }

    /** Builds the immutable context and runs the pure calculation ({@code §41, §73}). */
    public EvaluationOutcome calculate(AttemptEvaluationCatalog.AttemptView attempt, UUID answerKeyId) {
        AssessmentDefinitionCatalog.AssessmentScoringView scoring =
                assessment.requireScoringContext(attempt.assessmentVersionId());
        if (!scoring.published()) {
            throw new EvaluationNotAllowedException("The assessment version is not published");
        }
        List<AttemptEvaluationCatalog.SnapshotQuestionView> snapshot = delivery.snapshot(attempt.attemptId());
        if (snapshot.isEmpty()) {
            throw new EvaluationNotAllowedException("The attempt snapshot has no questions");
        }
        Map<UUID, AnswerKeyDetail> details = detailRepository.findByAnswerKeyId(answerKeyId).stream()
                .collect(Collectors.toMap(AnswerKeyDetail::getQuestionVersionId, Function.identity(), (a, b) -> a));
        Map<UUID, String> responses = delivery.responses(attempt.attemptId()).stream()
                .collect(Collectors.toMap(AttemptEvaluationCatalog.ResponseView::questionVersionId,
                        response -> response.response() == null ? "" : response.response(), (a, b) -> b));

        List<QuestionEvaluationContext> questions = new ArrayList<>(snapshot.size());
        for (AttemptEvaluationCatalog.SnapshotQuestionView question : snapshot) {
            AnswerKeyDetail detail = details.get(question.questionVersionId());
            if (detail == null) {
                throw new AnswerKeyIncompleteException(
                        "The answer key has no detail for question version " + question.questionVersionId());
            }
            ContentCatalog.PublishedQuestionVersion published =
                    content.requirePublishedQuestionVersion(question.questionVersionId());
            questions.add(new QuestionEvaluationContext(question.questionVersionId(), published.templateCode(),
                    question.marks(), question.negativeMarks(), responses.get(question.questionVersionId()),
                    detail.getQuestionOutcome(), detail.getCorrectOptionId(), detail.getCorrectAnswerText(),
                    detail.getAwardedMarks(), detail.getAwardedNegativeMarks(),
                    optionKeys(question.questionVersionId(), published.templateCode())));
        }
        return calculator.calculate(new EvaluationContext(attempt.attemptId(), attempt.assessmentVersionId(),
                scoring.totalMarks(), questions));
    }

    /** Persists a new evaluation version. The caller owns the surrounding transaction ({@code §84}). */
    public Evaluation persistEvaluation(AttemptEvaluationCatalog.AttemptView attempt, UUID answerKeyId,
            EvaluationOutcome outcome, int version) {
        Evaluation evaluation = new Evaluation();
        evaluation.setAttemptId(attempt.attemptId());
        evaluation.setAnswerKeyId(answerKeyId);
        evaluation.setVersion(version);
        evaluation.setEvaluatedOn(Instant.now());
        evaluation.setStatus(EvaluationStatus.COMPLETED);
        return evaluationRepository.saveAndFlush(evaluation);
    }

    /** Persists the result derived from the evaluation output ({@code §53}). */
    public Result persistResult(Evaluation evaluation, EvaluationOutcome outcome) {
        Result result = new Result();
        result.setEvaluationId(evaluation.getId());
        result.setObtainedMarks(outcome.obtainedMarks());
        result.setPercentage(outcome.percentage());
        result.setResultStatus(ResultStatus.CALCULATED);
        result.setRank(null);
        result.setPercentile(null);
        return resultRepository.saveAndFlush(result);
    }

    private Map<UUID, String> optionKeys(UUID questionVersionId, String templateCode) {
        if (templateCode == null || !OPTION_TEMPLATES.contains(templateCode)) {
            return Map.of();
        }
        return content.runtimeQuestion(questionVersionId).options().stream()
                .collect(Collectors.toMap(ContentCatalog.RuntimeOption::optionId,
                        ContentCatalog.RuntimeOption::optionKey, (a, b) -> a));
    }
}
