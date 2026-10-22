package com.company.exam.integration.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.exam.delivery.application.SessionExpiryService;
import com.company.exam.delivery.config.DeliverySessionProperties;
import com.company.exam.identity.entity.User;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * The assessment-session expiry sweep ({@code specs/14-P5-Delivery-V1.5.md §90, §91}).
 *
 * <p>A stale session is expired by a derived expiry, not by a stored column: either the idle timeout
 * elapsed with no recorded runtime activity, or the attempt deadline passed while the attempt was no
 * longer IN_PROGRESS. The sweep never mutates an attempt, and a repeated sweep is a no-op.
 */
class SessionExpiryIntegrationTest extends AbstractDeliveryIntegrationTest {

    /** The assessment version in this harness runs for 60 minutes, so two hours is comfortably past it. */
    private static final long EXPIRED_BY_MINUTES = 120;

    @Autowired
    private SessionExpiryService sessionExpiryService;
    @Autowired
    private DeliverySessionProperties sessionProperties;

    // ------------------------------------------------------------------ expiry

    @Test
    void aSessionPastItsIdleTimeoutIsExpired() throws Exception {
        Attempt started = startAttempt("p5-session-idle");
        backdateLastActivity(started.sessionId());

        sessionExpiryService.sweep(50);

        assertThat(sessionStatus(started.sessionId())).isEqualTo("EXPIRED");
        assertThat(sessionEndedOn(started.sessionId())).isNotNull();
        // The sweep expires the session only; the attempt is left exactly as it was.
        assertThat(attemptStatus(started.attemptId())).isEqualTo("IN_PROGRESS");
    }

    @Test
    void aSessionLeftActiveBehindAFinishedAttemptIsExpired() throws Exception {
        Attempt started = startAttempt("p5-session-finished");
        backdateStart(started.attemptId());
        // The attempt is over but the session was left ACTIVE behind it: exactly the orphan state the
        // sweep exists to clean up. The session is not idle, so only the finished-attempt rule can fire.
        jdbcTemplate.update("update student_attempt set attempt_status = 'SUBMITTED', submitted_on = now() "
                + "where id = ?", started.attemptId());

        sessionExpiryService.sweep(50);

        assertThat(sessionStatus(started.sessionId())).isEqualTo("EXPIRED");
        assertThat(sessionEndedOn(started.sessionId())).isNotNull();
    }

    @Test
    void aSessionInsideItsWindowIsUnaffected() throws Exception {
        Attempt started = startAttempt("p5-session-live");

        sessionExpiryService.sweep(50);

        assertThat(sessionStatus(started.sessionId())).isEqualTo("ACTIVE");
        assertThat(sessionEndedOn(started.sessionId())).isNull();
        assertThat(attemptStatus(started.attemptId())).isEqualTo("IN_PROGRESS");
    }

    @Test
    void anExpiredAttemptWithNoIdleSessionKeepsItsSessionUntilTheIdleTimeout() throws Exception {
        Attempt started = startAttempt("p5-session-distinct");
        backdateStart(started.attemptId());
        // The attempt deadline has passed but the attempt is still IN_PROGRESS: attempt expiry and
        // session expiry are distinct concepts (§91), and the attempt's own expiry path owns the
        // attempt transition. The session is not yet stale.
        sessionExpiryService.sweep(50);

        assertThat(sessionStatus(started.sessionId())).isEqualTo("ACTIVE");
        assertThat(attemptStatus(started.attemptId())).isEqualTo("IN_PROGRESS");
    }

    // ------------------------------------------------------------------ safety

    @Test
    void expiringAStaleSessionDoesNotStrandALiveAttempt() throws Exception {
        Attempt started = startAttempt("p5-session-live-attempt");
        backdateLastActivity(started.sessionId());

        sessionExpiryService.sweep(50);

        assertThat(sessionStatus(started.sessionId())).isEqualTo("EXPIRED");
        // The attempt is still IN_PROGRESS and inside its deadline, so the student keeps working:
        // a save does not depend on an ACTIVE session, and a Start Exam establishes a fresh one
        // (§91: a session may expire while an attempt remains recoverable until the deadline).
        mockMvc.perform(put("/api/v1/attempts/" + started.attemptId() + "/responses/"
                        + started.questionVersionId())
                        .header(AUTHORIZATION, bearer(started.token()))
                        .contentType(JSON).content("{\"response\":\"A\",\"timeSpentSeconds\":5}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/assessment-schedules/" + started.scheduleId() + "/start")
                        .header(AUTHORIZATION, bearer(started.token())))
                .andExpect(status().isOk());
        assertThat(activeSessionCount(started.attemptId())).isEqualTo(1);
        assertThat(attemptStatus(started.attemptId())).isEqualTo("IN_PROGRESS");
    }

    @Test
    void theSweepDoesNotModifyASubmittedAttempt() throws Exception {
        Attempt started = startAttempt("p5-session-submitted");
        mockMvc.perform(put("/api/v1/attempts/" + started.attemptId() + "/responses/"
                        + started.questionVersionId())
                        .header(AUTHORIZATION, bearer(started.token()))
                        .contentType(JSON).content("{\"response\":\"A\",\"timeSpentSeconds\":5}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/attempts/" + started.attemptId() + "/submit")
                        .header(AUTHORIZATION, bearer(started.token())))
                .andExpect(status().isOk());

        String statusBefore = attemptStatus(started.attemptId());
        Instant submittedBefore = submittedOn(started.attemptId());
        Timestamp updatedBefore = updatedOn(started.attemptId());
        String sessionBefore = sessionStatus(started.sessionId());

        sessionExpiryService.sweep(50);

        assertThat(attemptStatus(started.attemptId())).isEqualTo(statusBefore);
        assertThat(submittedOn(started.attemptId())).isEqualTo(submittedBefore);
        assertThat(updatedOn(started.attemptId())).isEqualTo(updatedBefore);
        assertThat(sessionStatus(started.sessionId())).isEqualTo(sessionBefore);
    }

    @Test
    void theSweepIsIdempotentOnRetry() throws Exception {
        Attempt started = startAttempt("p5-session-idempotent");
        backdateLastActivity(started.sessionId());

        assertThat(sessionExpiryService.expireIfStale(started.sessionId(), Instant.now())).isTrue();
        Instant endedOn = sessionEndedOn(started.sessionId());
        assertThat(endedOn).isNotNull();

        // A retry must not move the session again, rewrite ended_on, or duplicate the audit event.
        assertThat(sessionExpiryService.expireIfStale(started.sessionId(), Instant.now())).isFalse();
        assertThat(sessionEndedOn(started.sessionId())).isEqualTo(endedOn);
        assertThat(expiryAuditCount(started.sessionId())).isEqualTo(1);
    }

    // ------------------------------------------------------------------ helpers

    /** A started attempt with its session and first snapshot question. */
    private record Attempt(UUID attemptId, UUID sessionId, UUID questionVersionId, UUID userId, String token,
            String scheduleId) {
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
        return new Attempt(attemptId, sessionId, UUID.fromString(questionVersionId), student.getId(), token,
                scheduleId);
    }

    /** Moves the persisted start into the past so the authoritative deadline has passed. */
    private void backdateStart(UUID attemptId) {
        jdbcTemplate.update("update student_attempt set started_on = ? where id = ?",
                Timestamp.from(Instant.now().minus(EXPIRED_BY_MINUTES, ChronoUnit.MINUTES)), attemptId);
    }

    /** Moves the last recorded runtime activity beyond the configured idle timeout. */
    private void backdateLastActivity(UUID sessionId) {
        jdbcTemplate.update("update assessment_session set last_activity_on = ? where id = ?",
                Timestamp.from(Instant.now().minus(sessionProperties.getIdleTimeout())
                        .minus(1, ChronoUnit.MINUTES)), sessionId);
    }

    private String attemptStatus(UUID attemptId) {
        return jdbcTemplate.queryForObject("select attempt_status from student_attempt where id = ?", String.class,
                attemptId);
    }

    private String sessionStatus(UUID sessionId) {
        return jdbcTemplate.queryForObject("select session_status from assessment_session where id = ?",
                String.class, sessionId);
    }

    private long activeSessionCount(UUID attemptId) {
        Long count = jdbcTemplate.queryForObject(
                "select count(*) from assessment_session where attempt_id = ? and session_status = 'ACTIVE'",
                Long.class, attemptId);
        return count == null ? 0 : count;
    }

    private Instant sessionEndedOn(UUID sessionId) {
        Timestamp value = jdbcTemplate.queryForObject("select ended_on from assessment_session where id = ?",
                Timestamp.class, sessionId);
        return value == null ? null : value.toInstant();
    }

    private Instant submittedOn(UUID attemptId) {
        Timestamp value = jdbcTemplate.queryForObject("select submitted_on from student_attempt where id = ?",
                Timestamp.class, attemptId);
        return value == null ? null : value.toInstant();
    }

    private Timestamp updatedOn(UUID attemptId) {
        return jdbcTemplate.queryForObject("select updated_on from student_attempt where id = ?", Timestamp.class,
                attemptId);
    }

    private long expiryAuditCount(UUID sessionId) {
        Long count = jdbcTemplate.queryForObject(
                "select count(*) from audit_log where entity_name = 'AssessmentSession' and entity_id = ? "
                        + "and action = 'EXPIRE'", Long.class, sessionId);
        return count == null ? 0 : count;
    }
}
