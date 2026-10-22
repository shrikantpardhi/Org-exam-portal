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
    private EvaluationService evaluationService;

    // A real template so the claimed task actually runs and reaches the evaluation service.
    private final WorkerExecutionTemplate workerTemplate =
            new WorkerExecutionTemplate(new SimpleMeterRegistry());

    private final ObjectMapper objectMapper = new ObjectMapper();

    private EvaluationWorker worker;

    @BeforeEach
    void setUp() {
        worker = new EvaluationWorker(jobRepository, evaluationService, workerTemplate, objectMapper);
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
        when(jobRepository.claim(eq(jobId), eq(BackgroundJobStatus.QUEUED), eq(BackgroundJobStatus.RUNNING), any()))
                .thenReturn(1);

        worker.evaluateQueuedAttempts();

        verify(evaluationService).evaluate(attemptId, answerKeyId, CurrentAuditorProvider.SYSTEM_ACTOR_ID);
        verify(jobRepository).markCompleted(eq(jobId), eq(BackgroundJobStatus.COMPLETED), any(Instant.class),
                eq(attemptId.toString()));
        verify(jobRepository, never()).markFailed(any(UUID.class), any(BackgroundJobStatus.class), any(Instant.class),
                anyString(), anyString());
    }

    @Test
    void failsAJobThatCarriesNoAnswerKey() {
        UUID jobId = UUID.randomUUID();
        UUID attemptId = UUID.randomUUID();
        BackgroundJob job = job(jobId, attemptId, "{\"attemptId\":\"" + attemptId + "\"}");

        when(jobRepository.findByJobTypeAndStatusOrderByQueuedOnAsc(any(), eq(BackgroundJobStatus.QUEUED), any()))
                .thenReturn(List.of(job));
        when(jobRepository.claim(eq(jobId), eq(BackgroundJobStatus.QUEUED), eq(BackgroundJobStatus.RUNNING), any()))
                .thenReturn(1);

        worker.evaluateQueuedAttempts();

        verify(evaluationService, never()).evaluate(any(UUID.class), any(UUID.class), any(UUID.class));
        verify(jobRepository).markFailed(eq(jobId), eq(BackgroundJobStatus.FAILED), any(Instant.class),
                eq("ANSWER_KEY_REQUIRED"), anyString());
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
