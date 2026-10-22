package com.company.exam.integration.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.exam.identity.entity.User;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * GAP-073: a successful evaluation completes the Delivery-owned attempt
 * ({@code specs/14-P5-Delivery-V1.5.md §8, §13} with {@code specs/15-P6-Evaluation-V1.5.md §9, §10,
 * §53}).
 *
 * <p>After the evaluation the attempt is {@code EVALUATED} and {@code total_score} carries the same
 * value as the Result's {@code obtained_marks}.
 */
class AttemptCompletionIntegrationTest extends AbstractEvaluationIntegrationTest {

    @Test
    void aSuccessfulEvaluationAdvancesTheAttemptToEvaluatedWithTheTotalScore() throws Exception {
        String versionId = publishAssessmentVersion(1);
        String scheduleId = createSchedule(versionId, "EXAM");
        User student = newStudent("p6-completion");
        String token = tokenFor(student);
        prepareAttempts(scheduleId);
        UUID attemptId = startAttempt(scheduleId, token);
        UUID questionVersionId = snapshotQuestionVersionIds(attemptId).get(0);
        answer(attemptId, questionVersionId, "A", token);
        submit(attemptId, token);

        String keyId = publishAnswerKeyWithOptionA(attemptId, versionId);
        String resultId = evaluate(attemptId, keyId);

        assertThat(attemptStatus(attemptId)).isEqualTo("EVALUATED");
        BigDecimal obtainedMarks = jdbcTemplate.queryForObject("select obtained_marks from result where id = ?",
                BigDecimal.class, UUID.fromString(resultId));
        BigDecimal totalScore = jdbcTemplate.queryForObject("select total_score from student_attempt where id = ?",
                BigDecimal.class, attemptId);
        assertThat(totalScore).as("total_score carries the official score").isEqualByComparingTo(obtainedMarks);
        assertThat(totalScore).isEqualByComparingTo("2.00");
    }

    @Test
    void repeatingTheEvaluationIsSafeAndKeepsTheAttemptEvaluated() throws Exception {
        String versionId = publishAssessmentVersion(1);
        String scheduleId = createSchedule(versionId, "EXAM");
        User student = newStudent("p6-completion-repeat");
        String token = tokenFor(student);
        prepareAttempts(scheduleId);
        UUID attemptId = startAttempt(scheduleId, token);
        UUID questionVersionId = snapshotQuestionVersionIds(attemptId).get(0);
        answer(attemptId, questionVersionId, "A", token);
        submit(attemptId, token);

        String keyId = publishAnswerKeyWithOptionA(attemptId, versionId);
        String first = mockMvc.perform(post("/api/v1/attempts/" + attemptId + "/evaluate")
                        .header(AUTHORIZATION, bearer(adminToken())).contentType(JSON)
                        .content("{\"answerKeyId\":\"" + keyId + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String second = mockMvc.perform(post("/api/v1/attempts/" + attemptId + "/evaluate")
                        .header(AUTHORIZATION, bearer(adminToken())).contentType(JSON)
                        .content("{\"answerKeyId\":\"" + keyId + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        // The same evaluation is returned, and the attempt stays completed with one total score.
        assertThat(JsonPath.read(second, "$.data.id").toString())
                .isEqualTo(JsonPath.read(first, "$.data.id").toString());
        assertThat(attemptStatus(attemptId)).isEqualTo("EVALUATED");
        assertThat(jdbcTemplate.queryForObject("select total_score from student_attempt where id = ?",
                BigDecimal.class, attemptId)).isEqualByComparingTo("2.00");
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from evaluation where attempt_id = ? and superseded_by_id is null", Long.class,
                attemptId)).isEqualTo(1L);
    }

    @Test
    void aReEvaluationKeepsTheCompletedAttemptAndItsOfficialScore() throws Exception {
        String versionId = publishAssessmentVersion(1);
        String scheduleId = createSchedule(versionId, "EXAM");
        User student = newStudent("p6-completion-reeval");
        String token = tokenFor(student);
        prepareAttempts(scheduleId);
        UUID attemptId = startAttempt(scheduleId, token);
        UUID questionVersionId = snapshotQuestionVersionIds(attemptId).get(0);
        answer(attemptId, questionVersionId, "A", token);
        submit(attemptId, token);

        String keyId = publishAnswerKeyWithOptionA(attemptId, versionId);
        evaluate(attemptId, keyId);

        // A corrected key re-evaluates an already completed attempt (§46, §52).
        String correctedKeyId = createAnswerKey(versionId);
        addDetail(correctedKeyId, questionVersionId, "NORMAL", wrongOptionIdFor(questionVersionId), null, null);
        publishAnswerKey(correctedKeyId);
        mockMvc.perform(post("/api/v1/attempts/" + attemptId + "/re-evaluate")
                        .header(AUTHORIZATION, bearer(adminToken())).contentType(JSON)
                        .content("{\"answerKeyId\":\"" + correctedKeyId + "\",\"reason\":\"Corrected answer key\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.newVersion").value(2));

        assertThat(attemptStatus(attemptId)).isEqualTo("EVALUATED");
        // The Delivery-owned total score follows the new official score of the re-evaluation.
        BigDecimal afterReEval = jdbcTemplate.queryForObject("select total_score from student_attempt where id = ?",
                BigDecimal.class, attemptId);
        BigDecimal newObtained = jdbcTemplate.queryForObject(
                "select obtained_marks from result where evaluation_id = (select id from evaluation "
                        + "where attempt_id = ? and superseded_by_id is null)",
                BigDecimal.class, attemptId);
        assertThat(afterReEval).as("total_score follows the re-evaluated score").isEqualByComparingTo(newObtained);
        assertThat(afterReEval).isEqualByComparingTo("0.00");
    }

    private String attemptStatus(UUID attemptId) {
        return jdbcTemplate.queryForObject("select attempt_status from student_attempt where id = ?", String.class,
                attemptId);
    }
}
