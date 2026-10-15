package com.company.exam.integration.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.exam.identity.entity.User;
import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Controlled re-evaluation, versioning, supersession, and determinism
 * ({@code specs/15-P6-Evaluation-V1.5.md §46-§52, §56, §133, §135, §173, §183}).
 */
class ReEvaluationIntegrationTest extends AbstractEvaluationIntegrationTest {

    @Test
    void reEvaluationCreatesVersionNPlusOneAndSupersedesVersionN() throws Exception {
        String versionId = publishAssessmentVersion(1);
        String scheduleId = createSchedule(versionId, "EXAM");
        User student = newStudent("p6-reeval");
        String token = tokenFor(student);
        prepareAttempts(scheduleId);
        UUID attemptId = startAttempt(scheduleId, token);
        UUID questionVersionId = snapshotQuestionVersionIds(attemptId).get(0);

        // The student answered "B"; key v1 says "A" is correct, so the result is 0.00.
        answer(attemptId, questionVersionId, "B", token);
        submit(attemptId, token);

        String keyV1 = createAnswerKey(versionId);
        addDetail(keyV1, questionVersionId, "NORMAL", correctOptionIdFor(questionVersionId), null, null);
        publishAnswerKey(keyV1);
        String firstResultId = evaluate(attemptId, keyV1);
        publishResult(firstResultId);
        assertThat(obtainedMarksOf(firstResultId)).isEqualByComparingTo("0.00");

        // A corrected key v2 says "B" is correct.
        String keyV2 = createAnswerKey(versionId);
        addDetail(keyV2, questionVersionId, "NORMAL", wrongOptionIdFor(questionVersionId), null, null);
        publishAnswerKey(keyV2);

        String reEvaluated = mockMvc.perform(post("/api/v1/attempts/" + attemptId + "/re-evaluate")
                        .header(AUTHORIZATION, bearer(adminToken())).contentType(JSON)
                        .content("{\"answerKeyId\":\"" + keyV2 + "\",\"reason\":\"Corrected answer key v2\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.newVersion").value(2))
                .andExpect(jsonPath("$.data.previousEvaluationId").exists())
                .andReturn().getResponse().getContentAsString();
        String newEvaluationId = JsonPath.read(reEvaluated, "$.data.newEvaluationId");
        String newResultId = JsonPath.read(reEvaluated, "$.data.newResultId");
        assertThat(newResultId).isNotEqualTo(firstResultId);
        assertThat(marksOf(newResultId)).isEqualByComparingTo("2.00");

        // Exactly one current evaluation; the previous version is superseded and queryable.
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from evaluation where attempt_id = ? and superseded_by_id is null", Long.class,
                attemptId)).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject(
                "select superseded_by_id from evaluation where attempt_id = ? and version = 1", UUID.class,
                attemptId)).hasToString(newEvaluationId);
        assertThat(jdbcTemplate.queryForObject(
                "select status from evaluation where attempt_id = ? and version = 1", String.class,
                attemptId)).isEqualTo("SUPERSEDED");
        assertThat(jdbcTemplate.queryForObject("select result_status from result where id = ?", String.class,
                UUID.fromString(firstResultId))).isEqualTo("SUPERSEDED");
        assertThat(jdbcTemplate.queryForObject("select superseded_on from result where id = ?", java.sql.Timestamp.class,
                UUID.fromString(firstResultId))).isNotNull();

        // History returns both versions, newest first.
        mockMvc.perform(get("/api/v1/attempts/" + attemptId + "/evaluations")
                        .header(AUTHORIZATION, bearer(adminToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].version").value(2))
                .andExpect(jsonPath("$.data[1].version").value(1))
                .andExpect(jsonPath("$.data[1].supersededReason").value("Corrected answer key v2"));
    }

    @Test
    void reEvaluationWithTheSameKeyIsDeterministic() throws Exception {
        String versionId = publishAssessmentVersion(1);
        String scheduleId = createSchedule(versionId, "EXAM");
        User student = newStudent("p6-deterministic");
        String token = tokenFor(student);
        prepareAttempts(scheduleId);
        UUID attemptId = startAttempt(scheduleId, token);
        UUID questionVersionId = snapshotQuestionVersionIds(attemptId).get(0);
        answer(attemptId, questionVersionId, "A", token);
        submit(attemptId, token);
        String keyId = publishAnswerKeyWithOptionA(attemptId, versionId);
        String resultId = evaluate(attemptId, keyId);
        publishResult(resultId);

        mockMvc.perform(post("/api/v1/attempts/" + attemptId + "/re-evaluate")
                        .header(AUTHORIZATION, bearer(adminToken())).contentType(JSON)
                        .content("{\"answerKeyId\":\"" + keyId + "\",\"reason\":\"reproducibility\"}"))
                .andExpect(status().isOk());
        String fresh = mockMvc.perform(post("/api/v1/attempts/" + attemptId + "/re-evaluate")
                        .header(AUTHORIZATION, bearer(adminToken())).contentType(JSON)
                        .content("{\"answerKeyId\":\"" + keyId + "\",\"reason\":\"reproducibility\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        // Versions 2 and 3 of the same input produce identical scores.
        java.math.BigDecimal v2 = jdbcTemplate.queryForObject(
                "select r.obtained_marks from result r join evaluation e on r.evaluation_id = e.id "
                        + "where e.attempt_id = ? and e.version = 2", java.math.BigDecimal.class, attemptId);
        java.math.BigDecimal v3 = jdbcTemplate.queryForObject(
                "select r.obtained_marks from result r join evaluation e on r.evaluation_id = e.id "
                        + "where e.attempt_id = ? and e.version = 3", java.math.BigDecimal.class, attemptId);
        assertThat(v3).isEqualByComparingTo(v2);
        assertThat(((Number) JsonPath.read(fresh, "$.data.newVersion")).intValue()).isEqualTo(3);
    }

    @Test
    void reEvaluationWithoutAnExistingEvaluationIsRejected() throws Exception {
        String versionId = publishAssessmentVersion(1);
        String scheduleId = createSchedule(versionId, "EXAM");
        User student = newStudent("p6-reeval-none");
        String token = tokenFor(student);
        prepareAttempts(scheduleId);
        UUID attemptId = startAttempt(scheduleId, token);
        UUID questionVersionId = snapshotQuestionVersionIds(attemptId).get(0);
        answer(attemptId, questionVersionId, "A", token);
        submit(attemptId, token);
        String keyId = publishAnswerKeyWithOptionA(attemptId, versionId);

        mockMvc.perform(post("/api/v1/attempts/" + attemptId + "/re-evaluate")
                        .header(AUTHORIZATION, bearer(adminToken())).contentType(JSON)
                        .content("{\"answerKeyId\":\"" + keyId + "\",\"reason\":\"none\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].code").value("RE_EVALUATION_NOT_ALLOWED"));
    }

    private java.math.BigDecimal obtainedMarksOf(String resultId) {
        return jdbcTemplate.queryForObject("select obtained_marks from result where id = ?",
                java.math.BigDecimal.class, UUID.fromString(resultId));
    }

    private java.math.BigDecimal marksOf(String resultId) {
        return obtainedMarksOf(resultId);
    }
}
