package com.company.exam.evaluation.worker;

import com.company.exam.common.audit.CurrentAuditorProvider;
import com.company.exam.common.job.BackgroundJob;
import com.company.exam.common.job.BackgroundJobRepository;
import com.company.exam.common.job.BackgroundJobStatus;
import com.company.exam.delivery.application.EvaluationHandoffService;
import com.company.exam.evaluation.service.EvaluationService;
import com.company.exam.infrastructure.worker.WorkerExecutionTemplate;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The asynchronous evaluation worker ({@code specs/15-P6-Evaluation-V1.5.md §77-§81}).
 *
 * <p>It consumes QUEUED {@code background_job} rows of type {@code EVALUATION} created by the Delivery
 * submission handoff, claims each job with a race-safe conditional update, and evaluates it through
 * {@link EvaluationService} (which is itself evaluate-once, so a redelivered job is safe).
 *
 * <p>The submission handoff designates the answer key at submission time: it carries an explicit
 * {@code answerKeyId} in the job parameters, resolved by Evaluation to the highest published key for
 * the attempt's exact {@code AssessmentVersion} ({@code §25, §26}). The worker consumes that carried
 * key, so submit-triggered jobs are scored without ever defaulting to a "current published key". A
 * job that still arrives without a key (for example a malformed or legacy row) is failed with
 * {@code ANSWER_KEY_REQUIRED} rather than silently scored. The synchronous, explicit
 * {@code POST /attempts/{id}/evaluate} remains available for staff. The worker is disabled by
 * default ({@code app.evaluation.worker.enabled=false}).
 */
@Component
@ConditionalOnProperty(name = "app.evaluation.worker.enabled", havingValue = "true")
public class EvaluationWorker {

    private static final Logger log = LoggerFactory.getLogger(EvaluationWorker.class);
    private static final String ANSWER_KEY_REQUIRED = "ANSWER_KEY_REQUIRED";

    private final BackgroundJobRepository jobRepository;
    private final EvaluationService evaluationService;
    private final WorkerExecutionTemplate workerTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.evaluation.worker.batch-size:50}")
    private int batchSize = 50;

    public EvaluationWorker(BackgroundJobRepository jobRepository, EvaluationService evaluationService,
            WorkerExecutionTemplate workerTemplate, ObjectMapper objectMapper) {
        this.jobRepository = jobRepository;
        this.evaluationService = evaluationService;
        this.workerTemplate = workerTemplate;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelayString = "${app.evaluation.worker.interval:PT30S}",
            initialDelayString = "${app.evaluation.worker.initial-delay:PT15S}")
    public void evaluateQueuedAttempts() {
        List<BackgroundJob> jobs = jobRepository.findByJobTypeAndStatusOrderByQueuedOnAsc(
                EvaluationHandoffService.JOB_TYPE, BackgroundJobStatus.QUEUED, PageRequest.of(0, batchSize));
        for (BackgroundJob job : jobs) {
            if (jobRepository.claim(job.getId(), BackgroundJobStatus.QUEUED, BackgroundJobStatus.RUNNING,
                    Instant.now()) != 1) {
                continue;
            }
            process(job);
        }
    }

    private void process(BackgroundJob job) {
        UUID attemptId = job.getReferenceId();
        String answerKeyId = answerKeyIdOf(job);
        if (attemptId == null || answerKeyId == null) {
            jobRepository.markFailed(job.getId(), BackgroundJobStatus.FAILED, Instant.now(), ANSWER_KEY_REQUIRED,
                    "No explicit answerKeyId was supplied; evaluation requires an explicit answer key");
            return;
        }
        try {
            workerTemplate.execute("EVALUATION", () -> evaluationService.evaluate(attemptId,
                    UUID.fromString(answerKeyId), CurrentAuditorProvider.SYSTEM_ACTOR_ID));
            jobRepository.markCompleted(job.getId(), BackgroundJobStatus.COMPLETED, Instant.now(),
                    attemptId.toString());
        } catch (RuntimeException failure) {
            log.warn("Evaluation job {} failed: {}", job.getId(), failure.getMessage());
            jobRepository.markFailed(job.getId(), BackgroundJobStatus.FAILED, Instant.now(), "EVALUATION_FAILED",
                    safeMessage(failure));
        }
    }

    private String answerKeyIdOf(BackgroundJob job) {
        String parameters = job.getParameters();
        if (parameters == null || parameters.isBlank()) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(parameters);
            JsonNode answerKeyId = node.get("answerKeyId");
            return answerKeyId == null || answerKeyId.isNull() ? null : answerKeyId.asString();
        } catch (RuntimeException malformed) {
            return null;
        }
    }

    private String safeMessage(RuntimeException failure) {
        String message = failure.getMessage();
        return message == null ? failure.getClass().getSimpleName() : message;
    }
}
