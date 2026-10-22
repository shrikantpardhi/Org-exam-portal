package com.company.exam.delivery.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.company.exam.common.audit.CurrentAuditorProvider;
import com.company.exam.common.audit.SecurityEventLogger;
import com.company.exam.common.exception.InvalidStateException;
import com.company.exam.delivery.entity.AttemptStatus;
import com.company.exam.delivery.entity.StudentAttempt;
import com.company.exam.delivery.repository.StudentAttemptRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * The Delivery completion contract ({@code specs/14-P5-Delivery-V1.5.md §8, §13}).
 *
 * <p>It advances a SUBMITTED attempt to EVALUATED with the official total score, is safe to
 * repeat, and rejects an attempt that is not SUBMITTED.
 */
@ExtendWith(MockitoExtension.class)
class AttemptCompletionServiceTest {

    @Mock
    private StudentAttemptRepository attemptRepository;

    @Mock
    private SecurityEventLogger audit;

    @Mock
    private CurrentAuditorProvider currentAuditor;

    private AttemptCompletionService service;

    @BeforeEach
    void setUp() {
        service = new AttemptCompletionService(attemptRepository, audit, currentAuditor);
    }

    @Test
    void advancesASubmittedAttemptToEvaluatedAndAuditsIt() {
        UUID attemptId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        BigDecimal score = new BigDecimal("7.50");
        when(currentAuditor.getCurrentAuditor()).thenReturn(Optional.of(actorId));
        when(attemptRepository.markEvaluated(eq(attemptId), eq(AttemptStatus.SUBMITTED),
                eq(AttemptStatus.EVALUATED), eq(score), eq(actorId), any(Instant.class))).thenReturn(1);

        service.markEvaluated(attemptId, score, Instant.now());

        verify(audit).audit(eq(actorId), eq("EVALUATE"), eq("StudentAttempt"), eq(attemptId), anyString());
    }

    @Test
    void repeatingTheCallForTheSameScoreIsSafe() {
        UUID attemptId = UUID.randomUUID();
        BigDecimal score = new BigDecimal("7.50");
        when(currentAuditor.getCurrentAuditor()).thenReturn(Optional.empty());
        when(attemptRepository.markEvaluated(eq(attemptId), any(AttemptStatus.class), any(AttemptStatus.class),
                any(BigDecimal.class), any(UUID.class), any(Instant.class))).thenReturn(0);
        when(attemptRepository.findById(attemptId)).thenReturn(Optional.of(evaluatedAttempt(attemptId, score)));

        service.markEvaluated(attemptId, score, Instant.now());

        verify(audit, never()).audit(any(UUID.class), anyString(), anyString(), any(UUID.class), anyString());
    }

    @Test
    void rejectsAnAttemptThatIsNotSubmitted() {
        UUID attemptId = UUID.randomUUID();
        BigDecimal score = new BigDecimal("7.50");
        when(currentAuditor.getCurrentAuditor()).thenReturn(Optional.empty());
        when(attemptRepository.markEvaluated(eq(attemptId), any(AttemptStatus.class), any(AttemptStatus.class),
                any(BigDecimal.class), any(UUID.class), any(Instant.class))).thenReturn(0);
        StudentAttempt inProgress = new StudentAttempt();
        inProgress.setId(attemptId);
        inProgress.setAttemptStatus(AttemptStatus.IN_PROGRESS);
        when(attemptRepository.findById(attemptId)).thenReturn(Optional.of(inProgress));

        assertThatThrownBy(() -> service.markEvaluated(attemptId, score, Instant.now()))
                .isInstanceOf(InvalidStateException.class)
                .hasMessageContaining("SUBMITTED");
        verify(audit, never()).audit(any(UUID.class), anyString(), anyString(), any(UUID.class), anyString());
    }

    @Test
    void rejectsAnAlreadyEvaluatedAttemptCarryingADifferentScore() {
        UUID attemptId = UUID.randomUUID();
        when(currentAuditor.getCurrentAuditor()).thenReturn(Optional.empty());
        when(attemptRepository.markEvaluated(eq(attemptId), any(AttemptStatus.class), any(AttemptStatus.class),
                any(BigDecimal.class), any(UUID.class), any(Instant.class))).thenReturn(0);
        when(attemptRepository.findById(attemptId))
                .thenReturn(Optional.of(evaluatedAttempt(attemptId, new BigDecimal("2.00"))));

        assertThatThrownBy(() -> service.markEvaluated(attemptId, new BigDecimal("7.50"), Instant.now()))
                .isInstanceOf(InvalidStateException.class);
    }

    private static StudentAttempt evaluatedAttempt(UUID attemptId, BigDecimal score) {
        StudentAttempt attempt = new StudentAttempt();
        attempt.setId(attemptId);
        attempt.setAttemptStatus(AttemptStatus.EVALUATED);
        attempt.setTotalScore(score);
        return attempt;
    }

    @Test
    void refreshesTheScoreOfAnAlreadyEvaluatedAttempt() {
        UUID attemptId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        BigDecimal score = new BigDecimal("0.00");
        when(currentAuditor.getCurrentAuditor()).thenReturn(Optional.of(actorId));
        when(attemptRepository.refreshEvaluatedScore(eq(attemptId), eq(AttemptStatus.EVALUATED),
                eq(score), eq(actorId), any(Instant.class))).thenReturn(1);

        service.refreshScore(attemptId, score, Instant.now());

        verify(audit).audit(eq(actorId), eq("EVALUATE"), eq("StudentAttempt"), eq(attemptId), anyString());
    }

    @Test
    void repeatingAScoreRefreshForTheSameScoreIsSafe() {
        UUID attemptId = UUID.randomUUID();
        BigDecimal score = new BigDecimal("0.00");
        when(currentAuditor.getCurrentAuditor()).thenReturn(Optional.empty());
        when(attemptRepository.refreshEvaluatedScore(eq(attemptId), any(AttemptStatus.class),
                any(BigDecimal.class), any(UUID.class), any(Instant.class))).thenReturn(0);
        when(attemptRepository.findById(attemptId)).thenReturn(Optional.of(evaluatedAttempt(attemptId, score)));

        service.refreshScore(attemptId, score, Instant.now());

        verify(audit, never()).audit(any(UUID.class), anyString(), anyString(), any(UUID.class), anyString());
    }

    @Test
    void rejectsAScoreRefreshForAnAttemptThatIsNotEvaluated() {
        UUID attemptId = UUID.randomUUID();
        BigDecimal score = new BigDecimal("0.00");
        when(currentAuditor.getCurrentAuditor()).thenReturn(Optional.empty());
        when(attemptRepository.refreshEvaluatedScore(eq(attemptId), any(AttemptStatus.class),
                any(BigDecimal.class), any(UUID.class), any(Instant.class))).thenReturn(0);
        StudentAttempt inProgress = new StudentAttempt();
        inProgress.setId(attemptId);
        inProgress.setAttemptStatus(AttemptStatus.IN_PROGRESS);
        when(attemptRepository.findById(attemptId)).thenReturn(Optional.of(inProgress));

        assertThatThrownBy(() -> service.refreshScore(attemptId, score, Instant.now()))
                .isInstanceOf(InvalidStateException.class)
                .hasMessageContaining("EVALUATED");
        verify(audit, never()).audit(any(UUID.class), anyString(), anyString(), any(UUID.class), anyString());
    }
}
