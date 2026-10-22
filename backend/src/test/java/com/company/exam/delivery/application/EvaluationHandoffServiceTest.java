package com.company.exam.delivery.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.company.exam.common.job.BackgroundJob;
import com.company.exam.common.job.BackgroundJobRepository;
import com.company.exam.common.job.BackgroundJobStatus;
import com.company.exam.delivery.api.SubmitAnswerKeyResolver;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Proves the submit handoff carries the resolved answer key ({@code specs/15-P6 §26}). */
@ExtendWith(MockitoExtension.class)
class EvaluationHandoffServiceTest {

    @Mock
    private BackgroundJobRepository jobRepository;

    @Mock
    private SubmitAnswerKeyResolver answerKeyResolver;

    private EvaluationHandoffService handoff;

    @BeforeEach
    void setUp() {
        handoff = new EvaluationHandoffService(jobRepository, answerKeyResolver);
    }

    @Test
    void enqueuesAJobCarryingTheResolvedAnswerKey() {
        UUID attemptId = UUID.randomUUID();
        UUID version = UUID.randomUUID();
        UUID answerKeyId = UUID.randomUUID();
        when(answerKeyResolver.resolveForSubmit(version)).thenReturn(Optional.of(answerKeyId));
        BackgroundJob saved = new BackgroundJob();
        saved.setStatus(BackgroundJobStatus.QUEUED);
        when(jobRepository.save(any(BackgroundJob.class))).thenReturn(saved);
        when(jobRepository.findByJobTypeAndReferenceTypeAndReferenceId(
                EvaluationHandoffService.JOB_TYPE, EvaluationHandoffService.REFERENCE_TYPE, attemptId))
                .thenReturn(List.of());

        Optional<BackgroundJob> result = handoff.enqueue(attemptId, version);

        assertThat(result).contains(saved);
        org.mockito.ArgumentCaptor<BackgroundJob> captor =
                org.mockito.ArgumentCaptor.forClass(BackgroundJob.class);
        verify(jobRepository).save(captor.capture());
        assertThat(captor.getValue().getParameters())
                .contains("\"attemptId\":\"" + attemptId + "\"")
                .contains("\"answerKeyId\":\"" + answerKeyId + "\"");
    }

    @Test
    void doesNotEnqueueAndReturnsEmptyWhenNoPublishedKeyExists() {
        UUID attemptId = UUID.randomUUID();
        UUID version = UUID.randomUUID();
        when(answerKeyResolver.resolveForSubmit(version)).thenReturn(Optional.empty());
        when(jobRepository.findByJobTypeAndReferenceTypeAndReferenceId(
                EvaluationHandoffService.JOB_TYPE, EvaluationHandoffService.REFERENCE_TYPE, attemptId))
                .thenReturn(List.of());

        Optional<BackgroundJob> result = handoff.enqueue(attemptId, version);

        assertThat(result).isEmpty();
        verify(jobRepository, never()).save(any(BackgroundJob.class));
    }

    @Test
    void isIdempotentAndDoesNotReEnqueueOrReResolve() {
        UUID attemptId = UUID.randomUUID();
        UUID version = UUID.randomUUID();
        BackgroundJob existing = new BackgroundJob();
        when(jobRepository.findByJobTypeAndReferenceTypeAndReferenceId(
                EvaluationHandoffService.JOB_TYPE, EvaluationHandoffService.REFERENCE_TYPE, attemptId))
                .thenReturn(List.of(existing));

        Optional<BackgroundJob> result = handoff.enqueue(attemptId, version);

        assertThat(result).contains(existing);
        verify(answerKeyResolver, never()).resolveForSubmit(any(UUID.class));
    }
}
