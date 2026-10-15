package com.company.exam.integration.evaluation;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.exam.identity.entity.User;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * P6 security boundary ({@code specs/15-P6-Evaluation-V1.5.md §87, §140-§144, §181}).
 *
 * <p>Students cannot access answer keys, cannot evaluate or re-evaluate, cannot publish results, and
 * cannot read another student's result. Staff hold the explicit evaluation permissions.
 */
class EvaluationSecurityIntegrationTest extends AbstractEvaluationIntegrationTest {

    @Test
    void studentsAreDeniedAnswerKeyAndEvaluationEndpoints() throws Exception {
        String versionId = publishAssessmentVersion(1);
        String scheduleId = createSchedule(versionId, "EXAM");
        User student = newStudent("p6-security");
        String studentToken = tokenFor(student);
        prepareAttempts(scheduleId);
        UUID attemptId = startAttempt(scheduleId, studentToken);
        UUID questionVersionId = snapshotQuestionVersionIds(attemptId).get(0);
        answer(attemptId, questionVersionId, "A", studentToken);
        submit(attemptId, studentToken);
        String keyId = publishAnswerKeyWithOptionA(attemptId, versionId);
        String resultId = evaluate(attemptId, keyId);

        mockMvc.perform(get("/api/v1/answer-keys").header(AUTHORIZATION, bearer(studentToken)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/answer-keys/" + keyId).header(AUTHORIZATION, bearer(studentToken)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/answer-keys/" + keyId + "/details")
                        .header(AUTHORIZATION, bearer(studentToken)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/answer-keys").header(AUTHORIZATION, bearer(studentToken)).contentType(JSON)
                        .content("{\"assessmentVersionId\":\"" + versionId + "\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/attempts/" + attemptId + "/evaluate")
                        .header(AUTHORIZATION, bearer(studentToken)).contentType(JSON)
                        .content("{\"answerKeyId\":\"" + keyId + "\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/attempts/" + attemptId + "/re-evaluate")
                        .header(AUTHORIZATION, bearer(studentToken)).contentType(JSON)
                        .content("{\"answerKeyId\":\"" + keyId + "\",\"reason\":\"nope\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/results/" + resultId + "/publish")
                        .header(AUTHORIZATION, bearer(studentToken)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/results").header(AUTHORIZATION, bearer(studentToken)))
                .andExpect(status().isForbidden());

        // Staff hold the explicit evaluation permissions.
        mockMvc.perform(get("/api/v1/answer-keys").header(AUTHORIZATION, bearer(staffToken())))
                .andExpect(status().isOk());
    }

    @Test
    void aStudentCannotReadAnotherStudentsResult() throws Exception {
        String versionId = publishAssessmentVersion(1);
        String scheduleId = createSchedule(versionId, "EXAM");
        User owner = newStudent("p6-owner");
        User other = newStudent("p6-other");
        String ownerToken = tokenFor(owner);
        String otherToken = tokenFor(other);
        prepareAttempts(scheduleId);
        UUID attemptId = startAttempt(scheduleId, ownerToken);
        UUID questionVersionId = snapshotQuestionVersionIds(attemptId).get(0);
        answer(attemptId, questionVersionId, "A", ownerToken);
        submit(attemptId, ownerToken);
        String keyId = publishAnswerKeyWithOptionA(attemptId, versionId);
        String resultId = evaluate(attemptId, keyId);
        publishResult(resultId);

        // The owner sees it; another student does not (404, no existence leak).
        mockMvc.perform(get("/api/v1/attempts/" + attemptId + "/result")
                        .header(AUTHORIZATION, bearer(ownerToken)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/attempts/" + attemptId + "/result")
                        .header(AUTHORIZATION, bearer(otherToken)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/results/" + resultId).header(AUTHORIZATION, bearer(otherToken)))
                .andExpect(status().isNotFound());

        // Staff with RESULT_VIEW can read any result.
        mockMvc.perform(get("/api/v1/results/" + resultId).header(AUTHORIZATION, bearer(staffToken())))
                .andExpect(status().isOk());
    }
}
