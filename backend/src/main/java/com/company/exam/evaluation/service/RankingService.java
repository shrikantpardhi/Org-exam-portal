package com.company.exam.evaluation.service;

import com.company.exam.evaluation.entity.Result;
import com.company.exam.evaluation.entity.ResultStatus;
import com.company.exam.evaluation.observability.EvaluationMetrics;
import com.company.exam.evaluation.repository.AnswerKeyRepository;
import com.company.exam.evaluation.repository.ResultRepository;
import com.company.exam.evaluation.scoring.RoundingPolicy;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Rank and percentile recomputation ({@code specs/15-P6-Evaluation-V1.5.md §35, §36, §37, §38, §105}).
 *
 * <p>The population is the current (non-superseded) evaluation of every attempt bound to the same
 * AssessmentVersion whose Result is PUBLISHED. Rank uses standard competition ranking; percentile is
 * the share of the population scoring at or below the candidate, HALF_UP to 4 decimals. Both are
 * recomputed in one bounded pass per assessment version, serialized by a transaction-scoped
 * PostgreSQL advisory lock so concurrent publications never produce torn rankings.
 */
@Service
public class RankingService {

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final ResultRepository resultRepository;
    private final AnswerKeyRepository answerKeyRepository;
    private final EvaluationMetrics metrics;

    public RankingService(ResultRepository resultRepository, AnswerKeyRepository answerKeyRepository,
            EvaluationMetrics metrics) {
        this.resultRepository = resultRepository;
        this.answerKeyRepository = answerKeyRepository;
        this.metrics = metrics;
    }

    /**
     * Recomputes rank and percentile for every published result of an assessment version. Must run
     * inside a transaction so the advisory lock is held for the duration of the update
     * ({@code §38}). A no-op when no result of the version is published ({@code §37}).
     */
    @Transactional
    public void recompute(UUID assessmentVersionId) {
        long startedAt = System.nanoTime();
        lockAssessmentVersion(assessmentVersionId);
        List<Result> population = resultRepository.findPublishedForVersion(assessmentVersionId,
                ResultStatus.PUBLISHED);
        if (population.isEmpty()) {
            return;
        }
        population.sort(Comparator.comparing(this::scoreOf, Comparator.reverseOrder()));

        int total = population.size();
        int index = 0;
        while (index < total) {
            BigDecimal score = scoreOf(population.get(index));
            int groupEnd = index;
            while (groupEnd < total && scoreOf(population.get(groupEnd)).compareTo(score) == 0) {
                groupEnd++;
            }
            int rank = index + 1;
            BigDecimal percentile = RoundingPolicy.percentile(
                    BigDecimal.valueOf(total - index).multiply(HUNDRED).divide(BigDecimal.valueOf(total),
                            RoundingPolicy.PERCENTILE_SCALE, java.math.RoundingMode.HALF_UP));
            for (int position = index; position < groupEnd; position++) {
                Result result = population.get(position);
                result.setRank(rank);
                result.setPercentile(percentile);
            }
            index = groupEnd;
        }
        resultRepository.saveAll(population);
        metrics.rankRecomputed((System.nanoTime() - startedAt) / 1_000_000L);
    }

    private BigDecimal scoreOf(Result result) {
        return result.getObtainedMarks() == null ? ZERO : result.getObtainedMarks();
    }

    private void lockAssessmentVersion(UUID assessmentVersionId) {
        // A row lock over the evaluation-owned answer keys of the version serializes concurrent
        // recomputations without reaching into another module's tables (§38).
        answerKeyRepository.lockByAssessmentVersionId(assessmentVersionId);
    }
}
