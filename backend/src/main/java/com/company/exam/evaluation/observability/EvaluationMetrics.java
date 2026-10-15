package com.company.exam.evaluation.observability;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Evaluation observability counters ({@code specs/15-P6-Evaluation-V1.5.md §151}).
 *
 * <p>Low-cardinality metrics only. No answer-key mapping, response content, or student data is ever
 * used as a tag.
 */
@Component
public class EvaluationMetrics {

    public static final String EVALUATION_REQUESTS = "evaluation_requests_total";
    public static final String EVALUATION_SUCCESS = "evaluation_success_total";
    public static final String EVALUATION_FAILURE = "evaluation_failure_total";
    public static final String REEVALUATION_REQUESTS = "reevaluation_requests_total";
    public static final String REEVALUATION_SUCCESS = "reevaluation_success_total";
    public static final String RESULT_PUBLICATION = "result_publication_total";
    public static final String RESULT_PUBLICATION_FAILURE = "result_publication_failure_total";
    public static final String SCORECARD_GENERATION = "scorecard_generation_total";
    public static final String SCORECARD_GENERATION_FAILURE = "scorecard_generation_failure_total";
    public static final String RANK_RECOMPUTATION = "rank_recomputation_total";
    public static final String RANK_RECOMPUTATION_DURATION = "rank_recomputation_duration";

    private final MeterRegistry registry;

    public EvaluationMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void evaluationRequested() {
        registry.counter(EVALUATION_REQUESTS).increment();
    }

    public void evaluationSucceeded() {
        registry.counter(EVALUATION_SUCCESS).increment();
    }

    public void evaluationFailed() {
        registry.counter(EVALUATION_FAILURE).increment();
    }

    public void reevaluationRequested() {
        registry.counter(REEVALUATION_REQUESTS).increment();
    }

    public void reevaluationSucceeded() {
        registry.counter(REEVALUATION_SUCCESS).increment();
    }

    public void resultPublished() {
        registry.counter(RESULT_PUBLICATION).increment();
    }

    public void resultPublicationFailed() {
        registry.counter(RESULT_PUBLICATION_FAILURE).increment();
    }

    public void scorecardGenerated() {
        registry.counter(SCORECARD_GENERATION).increment();
    }

    public void scorecardGenerationFailed() {
        registry.counter(SCORECARD_GENERATION_FAILURE).increment();
    }

    public void rankRecomputed(long millis) {
        registry.counter(RANK_RECOMPUTATION).increment();
        registry.timer(RANK_RECOMPUTATION_DURATION).record(java.time.Duration.ofMillis(millis));
    }
}
