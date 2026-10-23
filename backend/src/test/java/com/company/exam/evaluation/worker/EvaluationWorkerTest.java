package com.company.exam.evaluation.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.company.exam.common.audit.CurrentAuditorProvider;
import com.company.exam.common.job.BackgroundJob;
import com.company.exam.common.job.BackgroundJobRepository;
import com.company.exam.common.job.BackgroundJobStatus;
import com.company.exam.common.job.JobStateService;
import com.company.exam.evaluation.service.EvaluationService;
import com.company.exam.infrastructure.worker.WorkerExecutionTemplate;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

/** Proves the worker consumes the answer key carried by the submit handoff ({@code specs/15-P6 §79}). */
@ExtendWith(MockitoExtension.class)
class EvaluationWorkerTest {

    @Mock
    private BackgroundJobRepository jobRepository;

    @Mock
    private JobStateService jobStateService;

    @Mock
    private EvaluationService evaluationService;

    // A real template so the claimed task actually runs and reaches the evaluation service.
    private final WorkerExecutionTemplate workerTemplate =
            new WorkerExecutionTemplate(new SimpleMeterRegistry());

    private final ObjectMapper objectMapper = new ObjectMapper();

    private EvaluationWorker worker;

    @BeforeEach
    void setUp() {
        worker = new EvaluationWorker(jobRepository, jobStateService, evaluationService, workerTemplate,
                objectMapper);
    }

    @Test
    void evaluatesAJobThatCarriesAnAnswerKey() {
        UUID jobId = UUID.randomUUID();
        UUID attemptId = UUID.randomUUID();
        UUID answerKeyId = UUID.randomUUID();
        BackgroundJob job = job(jobId, attemptId,
                "{\"attemptId\":\"" + attemptId + "\",\"answerKeyId\":\"" + answerKeyId + "\"}");

        when(jobRepository.findByJobTypeAndStatusOrderByQueuedOnAsc(any(), eq(BackgroundJobStatus.QUEUED), any()))
                .thenReturn(List.of(job));
        when(jobStateService.claim(jobId)).thenReturn(true);

        worker.evaluateQueuedAttempts();

        verify(evaluationService).evaluate(attemptId, answerKeyId, CurrentAuditorProvider.SYSTEM_ACTOR_ID);
        // Completion must go through the transactional wrapper as well.
        verify(jobStateService).complete(jobId, attemptId.toString());
        verify(jobRepository, never()).markCompleted(any(UUID.class), any(BackgroundJobStatus.class),
                any(Instant.class), anyString());
        verify(jobRepository, never()).markFailed(any(UUID.class), any(BackgroundJobStatus.class), any(Instant.class),
                anyString(), anyString());
        // A direct repository claim has no transaction and throws TransactionRequiredException on every
        // tick, which is how 992 EVALUATION jobs sat QUEUED for the whole 2026-10-23 ramp.
        verify(jobRepository, never()).claim(any(UUID.class), any(BackgroundJobStatus.class),
                any(BackgroundJobStatus.class), any(Instant.class));
    }

    @Test
    void failsAJobThatCarriesNoAnswerKey() {
        UUID jobId = UUID.randomUUID();
        UUID attemptId = UUID.randomUUID();
        BackgroundJob job = job(jobId, attemptId, "{\"attemptId\":\"" + attemptId + "\"}");

        when(jobRepository.findByJobTypeAndStatusOrderByQueuedOnAsc(any(), eq(BackgroundJobStatus.QUEUED), any()))
                .thenReturn(List.of(job));
        when(jobStateService.claim(jobId)).thenReturn(true);

        worker.evaluateQueuedAttempts();

        verify(evaluationService, never()).evaluate(any(UUID.class), any(UUID.class), any(UUID.class));
        verify(jobStateService).fail(eq(jobId), eq("ANSWER_KEY_REQUIRED"), anyString());
        verify(jobRepository, never()).claim(any(UUID.class), any(BackgroundJobStatus.class),
                any(BackgroundJobStatus.class), any(Instant.class));
    }

    @Test
    void skipsAJobAnotherWorkerAlreadyClaimed() {
        UUID jobId = UUID.randomUUID();
        UUID attemptId = UUID.randomUUID();
        UUID answerKeyId = UUID.randomUUID();
        BackgroundJob job = job(jobId, attemptId,
                "{\"attemptId\":\"" + attemptId + "\",\"answerKeyId\":\"" + answerKeyId + "\"}");

        when(jobRepository.findByJobTypeAndStatusOrderByQueuedOnAsc(any(), eq(BackgroundJobStatus.QUEUED), any()))
                .thenReturn(List.of(job));
        when(jobStateService.claim(jobId)).thenReturn(false);

        worker.evaluateQueuedAttempts();

        verify(evaluationService, never()).evaluate(any(UUID.class), any(UUID.class), any(UUID.class));
        verify(jobStateService, never()).complete(any(UUID.class), anyString());
    }

    private static BackgroundJob job(UUID id, UUID attemptId, String parameters) {
        BackgroundJob job = new BackgroundJob();
        job.setId(id);
        job.setReferenceId(attemptId);
        job.setStatus(BackgroundJobStatus.QUEUED);
        job.setParameters(parameters);
        return job;
    }
}
