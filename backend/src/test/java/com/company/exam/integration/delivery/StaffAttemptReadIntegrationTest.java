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
 * The staff cross-user attempt read ({@code specs/14-P5-Delivery-V1.5.md §83}).
 *
 * <p>Self-scoping stays the default: a student reads only their own attempt. A caller holding
 * {@code DELIVERY_ATTEMPT_VIEW_ANY} may read another student's runtime attempt. The widening is
 * granted by a permission resolved from the authenticated principal, never by anything in the
 * request, and the staff response is the same payload the owner sees.
 */
class StaffAttemptReadIntegrationTest extends AbstractDeliveryIntegrationTest {

    @Test
    void aStudentCannotReadAnotherStudentsAttempt() throws Exception {
        Attempt started = startAttempt("p5-staff-intruder");

        User intruder = newStudent("p5-staff-other-student");
        mockMvc.perform(get("/api/v1/attempts/" + started.attemptId())
                        .header(AUTHORIZATION, bearer(tokenFor(intruder))))
                .andExpect(status().isNotFound());
    }

    @Test
    void aStaffHolderWithThePermissionReadsAnotherStudentsAttempt() throws Exception {
        Attempt started = startAttempt("p5-staff-read");

        mockMvc.perform(get("/api/v1/attempts/" + started.attemptId())
                        .header(AUTHORIZATION, bearer(staffToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.attemptId").value(started.attemptId().toString()))
                .andExpect(jsonPath("$.data.attemptStatus").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.data.snapshotStatus").value("READY"));
    }

    @Test
    void theOwnerStillReadsTheirOwnAttempt() throws Exception {
        Attempt started = startAttempt("p5-staff-owner");

        mockMvc.perform(get("/api/v1/attempts/" + started.attemptId())
                        .header(AUTHORIZATION, bearer(started.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.attemptId").value(started.attemptId().toString()));
    }

    @Test
    void theStaffResponseOmitsCorrectAnswersAndPrivateAuditFields() throws Exception {
        Attempt started = startAttempt("p5-staff-leak");

        String body = mockMvc.perform(get("/api/v1/attempts/" + started.attemptId())
                        .header(AUTHORIZATION, bearer(staffToken())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // Correctness is never exposed, and the attempt's audit columns are not part of the payload.
        assertThat(body).doesNotContain("isCorrect");
        assertThat(body).doesNotContain("is_correct");
        assertThat(body).doesNotContain("answerKey");
        assertThat(body).doesNotContain("answer_key");
        assertThat(body).doesNotContain("correctAnswer");
        assertThat(body).doesNotContain("correctOption");
        assertThat(body).doesNotContain("createdBy");
        assertThat(body).doesNotContain("created_by");
        assertThat(body).doesNotContain("updatedBy");
        assertThat(body).doesNotContain("updated_by");
        assertThat(body).doesNotContain("createdOn");
        assertThat(body).doesNotContain("updatedOn");
        // The owner's identifier is not part of the attempt payload either.
        assertThat(body).doesNotContain(started.userId().toString());
    }

    // ------------------------------------------------------------------ helpers

    /** A started attempt with its first snapshot question. */
    private record Attempt(UUID attemptId, UUID questionVersionId, UUID userId, String token) {
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
        return new Attempt(attemptId, UUID.fromString(questionVersionId), student.getId(), token);
    }
}
