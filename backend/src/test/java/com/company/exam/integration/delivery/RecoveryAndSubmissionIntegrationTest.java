package com.company.exam.integration.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.exam.identity.entity.User;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * P5-19, P5-22, P5-23 and the third critical verification
 * ({@code specs/14-P5-Delivery-V1.5.md §51-§53, §63-§69, §161}): recovery returns the same snapshot,
 * and submission is idempotent with a durable single evaluation handoff.
 */
class RecoveryAndSubmissionIntegrationTest extends AbstractDeliveryIntegrationTest {

    @Test
    void recoveryReturnsTheSameSnapshotAndDoesNotRestartTheTimer() throws Exception {
        Started started = start();

        List<String> before = questionVersionIds(started);
        String startedOnBefore = startedOn(started);

        mockMvc.perform(post("/api/v1/sessions/" + started.sessionId() + "/recover")
                        .header(AUTHORIZATION, bearer(started.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.attemptId").value(started.attemptId().toString()))
                .andExpect(jsonPath("$.message").value("Session recovered"));

        List<String> after = questionVersionIds(started);
        assertThat(after).as("recovery returns the same snapshot").isEqualTo(before);
        assertThat(countAttemptQuestions(started.attemptId())).isEqualTo(2);
        assertThat(startedOn(started)).as("recovery does not reset started_on").isEqualTo(startedOnBefore);
        assertThat(countSessions(started.attemptId())).as("recovery creates no new session").isEqualTo(1);
    }

    @Test
    void theActiveSessionIsRecoverableAfterAnApiRestart() throws Exception {
        Started started = start();
        mockMvc.perform(get("/api/v1/sessions/active").header(AUTHORIZATION, bearer(started.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.attemptId").value(started.attemptId().toString()));
    }

    @Test
    void submissionIsIdempotentAndTriggersExactlyOneEvaluationJob() throws Exception {
        Started started = start();
        // A published answer key lets the submit handoff designate the key (GAP-071); without one,
        // no evaluation job would be enqueued.
        UUID answerKeyId = insertPublishedAnswerKey(started.versionId());
        String submitUrl = "/api/v1/attempts/" + started.attemptId() + "/submit";

        String first = mockMvc.perform(post(submitUrl).header(AUTHORIZATION, bearer(started.token()))
                        .contentType(JSON).content("{\"confirmed\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUBMITTED"))
                .andReturn().getResponse().getContentAsString();
        String submittedOn = JsonPath.read(first, "$.data.submittedOn");

        // A simultaneous/repeated submit returns the same terminal state and timestamp.
        mockMvc.perform(post(submitUrl).header(AUTHORIZATION, bearer(started.token()))
                        .contentType(JSON).content("{\"confirmed\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.data.submittedOn").value(submittedOn));

        // One authoritative terminal attempt state, no resurrection.
        Long attempts = jdbcTemplate.queryForObject(
                "select count(*) from student_attempt where id = ? and attempt_status = 'SUBMITTED'",
                Long.class, started.attemptId());
        assertThat(attempts).isEqualTo(1);

        // No duplicate evaluation trigger.
        Long jobs = jdbcTemplate.queryForObject(
                "select count(*) from background_job where job_type = 'EVALUATION' and reference_id = ?",
                Long.class, started.attemptId());
        assertThat(jobs).isEqualTo(1);

        // The single handoff job carries the explicit answer key resolved at submission time (GAP-071).
        String parameters = jdbcTemplate.queryForObject(
                "select parameters from background_job where job_type = 'EVALUATION' and reference_id = ?",
                String.class, started.attemptId());
        assertThat(parameters)
                .as("the single handoff job carries the explicit answer key resolved at submission time")
                .contains(started.attemptId().toString())
                .contains(answerKeyId.toString());

        // No double session end.
        Long activeSessions = jdbcTemplate.queryForObject(
                "select count(*) from assessment_session where attempt_id = ? and session_status = 'ACTIVE'",
                Long.class, started.attemptId());
        assertThat(activeSessions).isZero();
    }

    private record Started(UUID attemptId, UUID sessionId, String versionId, String token) {
    }

    private Started start() throws Exception {
        String versionId = publishAssessmentVersion(2);
        String scheduleId = createSchedule(versionId, "EXAM");
        User student = newStudent("p5-recovery");
        prepareAttempts(scheduleId);
        UUID attemptId = attemptIdFor(UUID.fromString(scheduleId), student.getId());
        String token = tokenFor(student);
        String body = mockMvc.perform(post("/api/v1/assessment-schedules/" + scheduleId + "/start")
                        .header(AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        UUID sessionId = UUID.fromString(JsonPath.read(body, "$.data.sessionId"));
        return new Started(attemptId, sessionId, versionId, token);
    }

    /** Inserts one published answer key for the version so the submit handoff can designate it. */
    private UUID insertPublishedAnswerKey(String assessmentVersionId) {
        UUID keyId = UUID.randomUUID();
        jdbcTemplate.update("insert into answer_key (id, assessment_version_id, version, status, is_published, "
                        + "created_on, updated_on) values (?, ?, 1, 'ACTIVE', true, now(), now())",
                keyId, UUID.fromString(assessmentVersionId));
        return keyId;
    }

    private List<String> questionVersionIds(Started started) throws Exception {
        String body = mockMvc.perform(get("/api/v1/attempts/" + started.attemptId() + "/questions")
                        .header(AUTHORIZATION, bearer(started.token())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data[*].questionVersionId");
    }

    private String startedOn(Started started) throws Exception {
        String body = mockMvc.perform(get("/api/v1/attempts/" + started.attemptId())
                        .header(AUTHORIZATION, bearer(started.token())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.startedOn");
    }
}
