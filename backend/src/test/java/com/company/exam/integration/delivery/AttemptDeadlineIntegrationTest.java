package com.company.exam.integration.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.exam.delivery.application.AttemptExpiryService;
import com.company.exam.delivery.application.SubmissionService;
import com.company.exam.identity.entity.User;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * The attempt deadline contract ({@code specs/14-P5-Delivery-V1.5.md §54, §56};
 * {@code specs/08-Exam-Engine-and-CDUI-Contract-V1.5.md §14, §67}).
 *
 * <p>A post-deadline mutation is refused with HTTP 409 {@code ATTEMPT_EXPIRED}, an expired attempt is
 * auto-submitted exactly once through the same server submission path a manual submit uses, and a
 * manual submit racing the expiry finalization produces one logical submission.
 */
class AttemptDeadlineIntegrationTest extends AbstractDeliveryIntegrationTest {

    /** The assessment version in this harness runs for 60 minutes, so two hours is comfortably past it. */
    private static final long EXPIRED_BY_MINUTES = 120;

    @Autowired
    private AttemptExpiryService attemptExpiryService;
    @Autowired
    private SubmissionService submissionService;

    // ------------------------------------------------------------------ enforcement

    @Test
    void responseSaveAfterTheDeadlineIsRejectedWithAttemptExpired() throws Exception {
        Attempt started = startAttempt("p13-expired-save");
        backdateStart(started.attemptId());

        mockMvc.perform(put("/api/v1/attempts/" + started.attemptId() + "/responses/" + started.questionVersionId())
                        .header(AUTHORIZATION, bearer(started.token()))
                        .contentType(JSON).content("{\"response\":\"A\",\"timeSpentSeconds\":5}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].code").value("ATTEMPT_EXPIRED"));
    }

    @Test
    void questionLoadAfterTheDeadlineIsRejected() throws Exception {
        Attempt started = startAttempt("p13-expired-question");
        backdateStart(started.attemptId());

        mockMvc.perform(get("/api/v1/attempts/" + started.attemptId() + "/questions")
                        .header(AUTHORIZATION, bearer(started.token())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].code").value("ATTEMPT_EXPIRED"));
    }

    @Test
    void sessionRecoveryAfterTheDeadlineIsRejected() throws Exception {
        Attempt started = startAttempt("p13-expired-recovery");
        backdateStart(started.attemptId());

        mockMvc.perform(post("/api/v1/sessions/" + started.sessionId() + "/recover")
                        .header(AUTHORIZATION, bearer(started.token())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].code").value("ATTEMPT_EXPIRED"));
    }

    @Test
    void responseSaveBeforeTheDeadlineIsUnaffected() throws Exception {
        Attempt started = startAttempt("p13-live-save");

        mockMvc.perform(put("/api/v1/attempts/" + started.attemptId() + "/responses/" + started.questionVersionId())
                        .header(AUTHORIZATION, bearer(started.token()))
                        .contentType(JSON).content("{\"response\":\"A\",\"timeSpentSeconds\":5}"))
                .andExpect(status().isOk());
    }

    @Test
    void questionLoadBeforeTheDeadlineIsUnaffected() throws Exception {
        Attempt started = startAttempt("p13-live-question");

        mockMvc.perform(get("/api/v1/attempts/" + started.attemptId() + "/questions")
                        .header(AUTHORIZATION, bearer(started.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    // ------------------------------------------------------------------ expiry auto-submit

    @Test
    void theExpirySweepSubmitsAStaleAttemptExactlyOnce() throws Exception {
        Attempt started = startAttempt("p13-expiry-sweep");
        backdateStart(started.attemptId());

        attemptExpiryService.sweep(50);
        Instant firstSubmittedOn = submittedOn(started.attemptId());

        assertThat(attemptStatus(started.attemptId())).isEqualTo("SUBMITTED");
        assertThat(firstSubmittedOn).isNotNull();
        assertThat(evaluationJobs(started.attemptId())).isEqualTo(1);

        // A second sweep must not finalize this attempt again: the submission timestamp is unchanged
        // and there is still exactly one evaluation handoff.
        attemptExpiryService.sweep(50);
        assertThat(submittedOn(started.attemptId())).isEqualTo(firstSubmittedOn);
        assertThat(evaluationJobs(started.attemptId())).isEqualTo(1);
    }

    @Test
    void theExpirySweepLeavesAnAttemptInsideItsDeadlineAlone() throws Exception {
        Attempt started = startAttempt("p13-expiry-not-due");

        attemptExpiryService.sweep(50);

        assertThat(attemptStatus(started.attemptId())).isEqualTo("IN_PROGRESS");
        assertThat(evaluationJobs(started.attemptId())).isZero();
        assertThat(submittedOn(started.attemptId())).isNull();
    }

    @Test
    void aManualSubmitRacingTheExpiryProducesOneSubmission() throws Exception {
        Attempt started = startAttempt("p13-expiry-race");
        backdateStart(started.attemptId());

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> manual = pool.submit(() -> {
                start.await();
                try {
                    submissionService.submit(started.attemptId(), started.userId());
                    return Boolean.TRUE;
                } catch (RuntimeException ignored) {
                    return Boolean.FALSE;
                }
            });
            Future<Integer> expiry = pool.submit(() -> {
                start.await();
                return attemptExpiryService.sweep(50);
            });
            start.countDown();
            manual.get();
            expiry.get();
        } finally {
            pool.shutdownNow();
        }

        assertThat(attemptStatus(started.attemptId())).isEqualTo("SUBMITTED");
        assertThat(evaluationJobs(started.attemptId())).isEqualTo(1);
        // The submission timestamp is stamped exactly once, by the winning finalization.
        assertThat(submittedOn(started.attemptId())).isNotNull();
    }

    // ------------------------------------------------------------------ helpers

    /** A started attempt with its session and first snapshot question. */
    private record Attempt(UUID attemptId, UUID sessionId, UUID questionVersionId, UUID userId, String token) {
    }

    private Attempt startAttempt(String prefix) throws Exception {
        // The student must exist BEFORE the attempts are prepared: preparation creates one attempt
        // per active candidate.
        User student = newStudent(prefix);
        String versionId = publishAssessmentVersion(1);
        String scheduleId = createSchedule(versionId, "EXAM");
        prepareAttempts(scheduleId);
        UUID attemptId = attemptIdFor(UUID.fromString(scheduleId), student.getId());
        String token = tokenFor(student);
        mockMvc.perform(post("/api/v1/assessment-schedules/" + scheduleId + "/start")
                        .header(AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk());
        String questionVersionId = jdbcTemplate.queryForObject(
                "select question_version_id from attempt_question where attempt_id = ?", String.class, attemptId);
        UUID sessionId = jdbcTemplate.queryForObject(
                "select id from assessment_session where attempt_id = ?", UUID.class, attemptId);
        return new Attempt(attemptId, sessionId, UUID.fromString(questionVersionId), student.getId(), token);
    }

    /** Moves the persisted start into the past so the authoritative deadline has passed. */
    private void backdateStart(UUID attemptId) {
        jdbcTemplate.update("update student_attempt set started_on = ? where id = ?",
                Timestamp.from(Instant.now().minus(EXPIRED_BY_MINUTES, ChronoUnit.MINUTES)), attemptId);
    }

    private String attemptStatus(UUID attemptId) {
        return jdbcTemplate.queryForObject("select attempt_status from student_attempt where id = ?", String.class,
                attemptId);
    }

    private Instant submittedOn(UUID attemptId) {
        Timestamp value = jdbcTemplate.queryForObject("select submitted_on from student_attempt where id = ?",
                Timestamp.class, attemptId);
        return value == null ? null : value.toInstant();
    }

    private long evaluationJobs(UUID attemptId) {
        Long count = jdbcTemplate.queryForObject(
                "select count(*) from background_job where job_type = 'EVALUATION' and reference_id = ?",
                Long.class, attemptId);
        return count == null ? 0 : count;
    }
}
