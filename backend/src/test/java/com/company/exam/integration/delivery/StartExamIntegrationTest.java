package com.company.exam.integration.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.exam.identity.entity.User;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * P5-13/P5-14 and the first critical verification
 * ({@code specs/14-P5-Delivery-V1.5.md §26-§29, §161}):
 * Start Exam performs zero snapshot inserts and {@code NOT_STARTED -> IN_PROGRESS} is race-safe.
 */
class StartExamIntegrationTest extends AbstractDeliveryIntegrationTest {

    @Test
    void startExamPerformsZeroSnapshotInsertsAndIsIdempotent() throws Exception {
        String versionId = publishAssessmentVersion(2);
        String scheduleId = createSchedule(versionId, "EXAM");
        User student = newStudent("p5-start");
        prepareAttempts(scheduleId);
        UUID attemptId = attemptIdFor(UUID.fromString(scheduleId), student.getId());

        long questionsBefore = countAttemptQuestions(attemptId);
        long optionsBefore = countOptions(attemptId);
        assertThat(questionsBefore).isEqualTo(2);
        assertThat(optionsBefore).isEqualTo(4);

        String token = tokenFor(student);
        mockMvc.perform(post("/api/v1/assessment-schedules/" + scheduleId + "/start")
                        .header(AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Assessment started"))
                .andExpect(jsonPath("$.data.attemptId").value(attemptId.toString()))
                .andExpect(jsonPath("$.data.assessmentVersionId").value(versionId))
                .andExpect(jsonPath("$.data.mode").value("EXAM"))
                .andExpect(jsonPath("$.data.timer.mode").value("GLOBAL"))
                .andExpect(jsonPath("$.data.deliveryConfiguration.navigation").exists());

        // The critical invariant: Start Exam created no snapshot rows.
        assertThat(countAttemptQuestions(attemptId)).isEqualTo(questionsBefore);
        assertThat(countOptions(attemptId)).isEqualTo(optionsBefore);
        assertThat(countSessions(attemptId)).isEqualTo(1);

        // A repeated Start returns the established context and creates no second session.
        mockMvc.perform(post("/api/v1/assessment-schedules/" + scheduleId + "/start")
                        .header(AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.attemptId").value(attemptId.toString()));
        assertThat(countSessions(attemptId)).isEqualTo(1);
        assertThat(countAttemptQuestions(attemptId)).isEqualTo(questionsBefore);
    }

    @Test
    void startExamReturns409WhenTheSnapshotIsNotPrepared() throws Exception {
        String versionId = publishAssessmentVersion(1);
        String scheduleId = createSchedule(versionId, "EXAM");
        User student = newStudent("p5-notready");
        // Preparation creates the PENDING attempt but does NOT materialize the snapshot.
        mockMvc.perform(post("/api/v1/assessment-schedules/" + scheduleId + "/prepare-attempts")
                        .header(AUTHORIZATION, bearer(adminToken())))
                .andExpect(status().isAccepted());

        mockMvc.perform(post("/api/v1/assessment-schedules/" + scheduleId + "/start")
                        .header(AUTHORIZATION, bearer(tokenFor(student))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].code").value("PREPARATION_NOT_READY"));
    }

    @Test
    void startExamReturns409WhenNoAttemptExists() throws Exception {
        String versionId = publishAssessmentVersion(1);
        String scheduleId = createSchedule(versionId, "EXAM");
        User student = newStudent("p5-noattempt");

        mockMvc.perform(post("/api/v1/assessment-schedules/" + scheduleId + "/start")
                        .header(AUTHORIZATION, bearer(tokenFor(student))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].code").value("PREPARATION_NOT_READY"));
    }

    @Test
    void aStudentCannotReadAnotherStudentsAttempt() throws Exception {
        String versionId = publishAssessmentVersion(1);
        String scheduleId = createSchedule(versionId, "EXAM");
        User owner = newStudent("p5-owner");
        prepareAttempts(scheduleId);
        UUID attemptId = attemptIdFor(UUID.fromString(scheduleId), owner.getId());

        User intruder = newStudent("p5-intruder");
        mockMvc.perform(get("/api/v1/attempts/" + attemptId).header(AUTHORIZATION, bearer(tokenFor(intruder))))
                .andExpect(status().isNotFound());
    }

    @Test
    void runtimeQuestionPayloadNeverExposesCorrectAnswers() throws Exception {
        String versionId = publishAssessmentVersion(1);
        String scheduleId = createSchedule(versionId, "EXAM");
        User student = newStudent("p5-leak");
        prepareAttempts(scheduleId);
        UUID attemptId = attemptIdFor(UUID.fromString(scheduleId), student.getId());
        String token = tokenFor(student);
        mockMvc.perform(post("/api/v1/assessment-schedules/" + scheduleId + "/start")
                .header(AUTHORIZATION, bearer(token))).andExpect(status().isOk());

        String body = mockMvc.perform(get("/api/v1/attempts/" + attemptId + "/questions")
                        .header(AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].attemptQuestionId").exists())
                .andExpect(jsonPath("$.data[0].options[0].optionKey").exists())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("isCorrect").doesNotContain("is_correct").doesNotContain("answerKey");
    }

    private long countOptions(UUID attemptId) {
        Long count = jdbcTemplate.queryForObject("""
                select count(*) from attempt_question_option o
                join attempt_question q on o.attempt_question_id = q.id
                where q.attempt_id = ?
                """, Long.class, attemptId);
        return count == null ? 0 : count;
    }
}
