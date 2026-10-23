package com.company.exam.delivery.application;

import com.company.exam.assessment.api.AssessmentDefinitionCatalog;
import com.company.exam.assessment.entity.ScheduleMode;
import com.company.exam.common.job.BackgroundJob;
import com.company.exam.common.job.BackgroundJobRepository;
import com.company.exam.common.job.BackgroundJobStatus;
import com.company.exam.content.api.ContentCatalog;
import com.company.exam.delivery.entity.AttemptQuestion;
import com.company.exam.delivery.entity.AttemptQuestionOption;
import com.company.exam.delivery.entity.AttemptStatus;
import com.company.exam.delivery.entity.SnapshotStatus;
import com.company.exam.delivery.entity.StudentAttempt;
import com.company.exam.delivery.repository.AttemptQuestionOptionRepository;
import com.company.exam.delivery.repository.AttemptQuestionRepository;
import com.company.exam.delivery.repository.StudentAttemptRepository;
import com.company.exam.identity.api.CandidateDirectory;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Asynchronous snapshot preparation ({@code specs/14-P5-Delivery-V1.5.md §20-§25, §30}).
 *
 * <p>Preparation is never performed inside Start Exam. Each attempt is prepared in explicit,
 * separate transactions: a claim transaction, a materialization transaction that also marks READY,
 * and — on failure — an independent transaction that marks FAILED. The claim uses a conditional
 * update, so only one worker owns an attempt regardless of instance count.
 */
@Service
public class SnapshotPreparationService {

    private static final Logger log = LoggerFactory.getLogger(SnapshotPreparationService.class);

    private final StudentAttemptRepository attemptRepository;
    private final AttemptQuestionRepository questionRepository;
    private final AttemptQuestionOptionRepository optionRepository;
    private final AssessmentDefinitionCatalog assessmentCatalog;
    private final ContentCatalog contentCatalog;
    private final CandidateDirectory candidateDirectory;
    private final BackgroundJobRepository jobRepository;
    private final TransactionTemplate transactionTemplate;
    private final com.company.exam.delivery.observability.DeliveryMetrics metrics;

    public SnapshotPreparationService(StudentAttemptRepository attemptRepository,
            AttemptQuestionRepository questionRepository, AttemptQuestionOptionRepository optionRepository,
            AssessmentDefinitionCatalog assessmentCatalog, ContentCatalog contentCatalog,
            CandidateDirectory candidateDirectory, BackgroundJobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            com.company.exam.delivery.observability.DeliveryMetrics metrics) {
        this.attemptRepository = attemptRepository;
        this.questionRepository = questionRepository;
        this.optionRepository = optionRepository;
        this.assessmentCatalog = assessmentCatalog;
        this.contentCatalog = contentCatalog;
        this.candidateDirectory = candidateDirectory;
        this.jobRepository = jobRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.metrics = metrics;
    }

    /**
     * Records the durable preparation request ({@code §44.1, §101}). The response carries this job
     * id; the worker performs the actual preparation asynchronously.
     */
    @Transactional
    public BackgroundJob enqueuePreparationJob(UUID scheduleId, UUID actorId) {
        BackgroundJob job = new BackgroundJob();
        job.setJobType(com.company.exam.common.job.JobTypes.ATTEMPT_PREPARATION);
        job.setReferenceType("ASSESSMENT_SCHEDULE");
        job.setReferenceId(scheduleId);
        job.setInitiatedBy(actorId);
        job.setStatus(BackgroundJobStatus.QUEUED);
        job.setParameters("{\"scheduleId\":\"" + scheduleId + "\"}");
        job.setQueuedOn(Instant.now());
        job.setProgressCurrent(0);
        job.setProgressTotal(0);
        job.setAttemptCount(0);
        return jobRepository.save(job);
    }

    /**
     * Creates one PENDING StudentAttempt per eligible candidate that does not already have one
     * ({@code §19, §30}). Idempotent: a re-run creates no duplicates.
     */
    @Transactional
    public int createPendingAttemptsForSchedule(UUID scheduleId, UUID actorId) {
        AssessmentDefinitionCatalog.ScheduleView schedule = assessmentCatalog.requireSchedule(scheduleId);
        assessmentCatalog.requirePublishedVersion(schedule.assessmentVersionId());
        int created = 0;
        for (UUID userId : candidateDirectory.activeCandidateUserIds()) {
            if (!attemptRepository.findByAssessmentScheduleIdAndUserId(scheduleId, userId).isEmpty()) {
                continue;
            }
            StudentAttempt attempt = new StudentAttempt();
            attempt.setUserId(userId);
            attempt.setAssessmentVersionId(schedule.assessmentVersionId());
            attempt.setAssessmentScheduleId(scheduleId);
            attempt.setAttemptMode(schedule.mode() == null ? ScheduleMode.EXAM : schedule.mode());
            attempt.setAttemptStatus(AttemptStatus.NOT_STARTED);
            attempt.setSnapshotStatus(SnapshotStatus.PENDING);
            attemptRepository.save(attempt);
            created++;
        }
        return created;
    }

    /**
     * Races to claim one attempt and materialize its snapshot in separate transactions.
     *
     * @return {@code true} when this caller owned and completed preparation.
     */
    public boolean prepare(UUID attemptId) {
        long startedNanos = System.nanoTime();
        Boolean claimed = transactionTemplate.execute(status -> attemptRepository.claimForPreparation(attemptId,
                SnapshotStatus.PENDING, SnapshotStatus.PREPARING, Instant.now()) == 1);
        if (!Boolean.TRUE.equals(claimed)) {
            return false;
        }
        try {
            transactionTemplate.executeWithoutResult(status -> {
                StudentAttempt attempt = attemptRepository.findById(attemptId).orElseThrow();
                materialize(attempt);
                attemptRepository.markSnapshotReady(attemptId, SnapshotStatus.PREPARING, SnapshotStatus.READY,
                        Instant.now());
            });
            metrics.snapshotPrepared();
            metrics.snapshotPreparationDuration(Duration.ofNanos(System.nanoTime() - startedNanos));
            return true;
        } catch (RuntimeException failure) {
            log.error("Snapshot preparation failed for attemptId={}", attemptId, failure);
            transactionTemplate.executeWithoutResult(status -> attemptRepository.markSnapshotFailed(attemptId,
                    SnapshotStatus.PREPARING, SnapshotStatus.FAILED, Instant.now()));
            return false;
        }
    }

    /** Prepares a bounded batch of PENDING attempts for the worker ({@code §103}). */
    public int preparePendingBatch(int limit) {
        List<UUID> pending = attemptRepository.findPendingIds(SnapshotStatus.PENDING, PageRequest.of(0, limit));
        int prepared = 0;
        for (UUID attemptId : pending) {
            if (prepare(attemptId)) {
                prepared++;
            }
        }
        return prepared;
    }

    /** The pending-snapshot backlog, published as the delivery backlog gauge (P12 §113). */
    public long pendingSnapshotCount() {
        return attemptRepository.countBySnapshotStatus(SnapshotStatus.PENDING);
    }

    /** Reclaims attempts whose PREPARING worker died ({@code §23, §115}). */
    @Transactional
    public int reclaimStale(Duration timeout) {
        List<UUID> stale = attemptRepository.findStalePreparing(SnapshotStatus.PREPARING,
                Instant.now().minus(timeout));
        for (UUID attemptId : stale) {
            attemptRepository.markSnapshotFailed(attemptId, SnapshotStatus.PREPARING, SnapshotStatus.FAILED,
                    Instant.now());
        }
        return stale.size();
    }

    /** Explicit retry policy: FAILED attempts return to PENDING and are re-prepared ({@code §23}). */
    public int retryFailed(UUID scheduleId) {
        List<StudentAttempt> failed = attemptRepository
                .findByAssessmentScheduleIdAndSnapshotStatus(scheduleId, SnapshotStatus.FAILED);
        int retried = 0;
        for (StudentAttempt attempt : failed) {
            UUID attemptId = attempt.getId();
            if (Boolean.TRUE.equals(resetForRetry(attemptId)) && prepare(attemptId)) {
                retried++;
            }
        }
        return retried;
    }

    /**
     * Resets one FAILED attempt to PENDING and clears the partial snapshot it left behind.
     *
     * <p>The clear is required, not cosmetic: {@code attempt_question} carries
     * {@code UNIQUE(attempt_id, question_version_id)} and {@code UNIQUE(attempt_id, display_order)}, so
     * a retry that inserted over the leftovers of the failed run would violate a unique constraint and
     * could never succeed. Both statements share one transaction so an attempt is never left PENDING
     * with a stale snapshot.
     *
     * @return {@code true} when this caller owned the reset
     */
    private Boolean resetForRetry(UUID attemptId) {
        return transactionTemplate.execute(status -> {
            if (attemptRepository.resetForRetry(attemptId, SnapshotStatus.FAILED, SnapshotStatus.PENDING,
                    Instant.now()) != 1) {
                return false;
            }
            optionRepository.deleteByAttemptId(attemptId);
            questionRepository.deleteByAttemptId(attemptId);
            return true;
        });
    }

    /**
     * Periodic retry sweep for FAILED snapshots ({@code §23}, GAP-157).
     *
     * <p>{@link #retryFailed(UUID)} is per-schedule and has no caller, so a failure that was not
     * retried explicitly stayed FAILED forever and blocked that candidate's Start Exam indefinitely.
     * This sweep is the missing automated caller.
     *
     * <p>It is deliberately bounded in three independent ways, because the frozen schema stores no
     * retry counter:
     *
     * <ol>
     *   <li>a per-run batch limit, so one tick cannot monopolize the worker;</li>
     *   <li>a cool-down since the last failure, so a retry is not a zero-information re-run of the
     *       same failure and successive retries of one attempt are spaced;</li>
     *   <li>a maximum age since attempt creation, after which the attempt is left FAILED for an
     *       operator instead of being retried forever.</li>
     * </ol>
     *
     * <p>Correctness is unchanged: the reset is still a conditional {@code FAILED -> PENDING} update,
     * so a concurrent explicit retry or another worker instance simply loses the race and affects zero
     * rows. Returns the number of attempts successfully re-prepared.
     */
    public int retryFailedSweep(int limit, Duration coolDown, Duration maxAge) {
        Instant now = Instant.now();
        List<UUID> candidates = attemptRepository.findRetryCandidateIds(SnapshotStatus.FAILED,
                now.minus(coolDown), now.minus(maxAge), PageRequest.of(0, limit));
        int retried = 0;
        for (UUID attemptId : candidates) {
            if (Boolean.TRUE.equals(resetForRetry(attemptId)) && prepare(attemptId)) {
                retried++;
            }
        }
        return retried;
    }

    /**
     * Materializes the immutable snapshot from the published definition and validates its integrity
     * before READY ({@code §21, §24, §25, §99}).
     */
    private void materialize(StudentAttempt attempt) {
        AssessmentDefinitionCatalog.PublishedAssessmentVersion version = assessmentCatalog
                .requirePublishedVersion(attempt.getAssessmentVersionId());
        List<AssessmentDefinitionCatalog.AssessmentQuestionView> definition = assessmentCatalog
                .questions(version.id());
        if (definition.isEmpty()) {
            throw new IllegalStateException("Published assessment has no questions");
        }
        Instant now = Instant.now();
        int fallbackOrder = 0;
        for (AssessmentDefinitionCatalog.AssessmentQuestionView question : definition) {
            ContentCatalog.RuntimeQuestion content = contentCatalog.runtimeQuestion(question.questionVersionId());
            if (content.options().isEmpty()) {
                throw new IllegalStateException("Question version has no options: " + question.questionVersionId());
            }
            AttemptQuestion row = new AttemptQuestion();
            row.setAttemptId(attempt.getId());
            row.setQuestionVersionId(question.questionVersionId());
            row.setDisplayOrder(question.displayOrder() == null ? ++fallbackOrder : question.displayOrder());
            row.setSectionName(question.sectionName());
            row.setMarks(question.marks());
            row.setNegativeMarks(question.negativeMarks());
            row.setCreatedOn(now);
            AttemptQuestion saved = questionRepository.save(row);

            int fallbackOptionOrder = 0;
            for (ContentCatalog.RuntimeOption option : content.options()) {
                AttemptQuestionOption snapshotOption = new AttemptQuestionOption();
                snapshotOption.setAttemptQuestionId(saved.getId());
                snapshotOption.setQuestionOptionId(option.optionId());
                snapshotOption.setDisplayOrder(option.displayOrder() == null
                        ? ++fallbackOptionOrder
                        : option.displayOrder());
                optionRepository.save(snapshotOption);
            }
            if (optionRepository.countByAttemptQuestionId(saved.getId()) != content.options().size()) {
                throw new IllegalStateException("Option snapshot is incomplete for " + question.questionVersionId());
            }
        }
        if (questionRepository.countByAttemptId(attempt.getId()) != definition.size()) {
            throw new IllegalStateException("Question snapshot is incomplete for attempt " + attempt.getId());
        }
    }
}
