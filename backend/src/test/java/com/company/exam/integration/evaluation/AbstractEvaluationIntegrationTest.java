package com.company.exam.integration.evaluation;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.exam.evaluation.repository.AnswerKeyDetailRepository;
import com.company.exam.evaluation.repository.AnswerKeyRepository;
import com.company.exam.evaluation.repository.EvaluationRepository;
import com.company.exam.evaluation.repository.ResultRepository;
import com.company.exam.evaluation.repository.ScorecardRepository;
import com.company.exam.integration.delivery.AbstractDeliveryIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Shared setup for P6 Evaluation integration tests.
 *
 * <p>It reuses the P3/P4/P5 authoring and delivery harness from {@link AbstractDeliveryIntegrationTest}
 * and adds the Evaluation-owned flow: answer-key authoring and publication, attempt start, response
 * save, submission, evaluation, result publication, and scorecard generation, all through the frozen
 * HTTP contract.
 */
public abstract class AbstractEvaluationIntegrationTest extends AbstractDeliveryIntegrationTest {

    @Autowired
    protected AnswerKeyRepository answerKeyRepository;
    @Autowired
    protected AnswerKeyDetailRepository answerKeyDetailRepository;
    @Autowired
    protected EvaluationRepository evaluationRepository;
    @Autowired
    protected ResultRepository resultRepository;
    @Autowired
    protected ScorecardRepository scorecardRepository;

    // ------------------------------------------------------------------ snapshot helpers

    protected List<UUID> snapshotQuestionVersionIds(UUID attemptId) {
        return jdbcTemplate.queryForList(
                "select question_version_id from attempt_question where attempt_id = ? order by display_order asc",
                UUID.class, attemptId);
    }

    protected UUID correctOptionIdFor(UUID questionVersionId) {
        // The shared harness authors option "A" as the single correct option.
        return jdbcTemplate.queryForObject(
                "select id from question_option where question_version_id = ? and option_key = 'A'",
                UUID.class, questionVersionId);
    }

    protected UUID wrongOptionIdFor(UUID questionVersionId) {
        return jdbcTemplate.queryForObject(
                "select id from question_option where question_version_id = ? and option_key = 'B'",
                UUID.class, questionVersionId);
    }

    protected BigDecimal attemptQuestionMarks(UUID attemptId, UUID questionVersionId) {
        return jdbcTemplate.queryForObject(
                "select marks from attempt_question where attempt_id = ? and question_version_id = ?",
                BigDecimal.class, attemptId, questionVersionId);
    }

    // ------------------------------------------------------------------ answer keys

    protected String createAnswerKey(String assessmentVersionId) throws Exception {
        String admin = adminToken();
        String body = "{\"assessmentVersionId\":\"" + assessmentVersionId + "\"}";
        String created = mockMvc.perform(post("/api/v1/answer-keys")
                        .header(AUTHORIZATION, bearer(admin)).contentType(JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(created, "$.data.id");
    }

    protected String addDetail(String answerKeyId, UUID questionVersionId, String outcome, UUID correctOptionId,
            BigDecimal awardedMarks, BigDecimal awardedNegativeMarks) throws Exception {
        String admin = adminToken();
        StringBuilder body = new StringBuilder("{\"questionVersionId\":\"").append(questionVersionId).append("\",");
        body.append("\"questionOutcome\":\"").append(outcome).append("\",");
        if (correctOptionId != null) {
            body.append("\"correctOptionId\":\"").append(correctOptionId).append("\",");
        }
        if (awardedMarks != null) {
            body.append("\"awardedMarks\":").append(awardedMarks).append(",");
        }
        if (awardedNegativeMarks != null) {
            body.append("\"awardedNegativeMarks\":").append(awardedNegativeMarks).append(",");
        }
        body.append("\"remarks\":null}");
        String created = mockMvc.perform(post("/api/v1/answer-keys/" + answerKeyId + "/details")
                        .header(AUTHORIZATION, bearer(admin)).contentType(JSON).content(body.toString()))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(created, "$.data.id");
    }

    protected void publishAnswerKey(String answerKeyId) throws Exception {
        mockMvc.perform(post("/api/v1/answer-keys/" + answerKeyId + "/publish")
                        .header(AUTHORIZATION, bearer(adminToken())))
                .andExpect(status().isOk());
    }

    /** Creates a key whose every snapshot question is NORMAL with option A correct. */
    protected String publishAnswerKeyWithOptionA(UUID attemptId, String assessmentVersionId) throws Exception {
        String keyId = createAnswerKey(assessmentVersionId);
        for (UUID questionVersionId : snapshotQuestionVersionIds(attemptId)) {
            addDetail(keyId, questionVersionId, "NORMAL", correctOptionIdFor(questionVersionId), null, null);
        }
        publishAnswerKey(keyId);
        return keyId;
    }

    // ------------------------------------------------------------------ runtime flow

    protected UUID startAttempt(String scheduleId, String studentToken) throws Exception {
        String body = mockMvc.perform(post("/api/v1/assessment-schedules/" + scheduleId + "/start")
                        .header(AUTHORIZATION, bearer(studentToken)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(body, "$.data.attemptId"));
    }

    protected void answer(UUID attemptId, UUID questionVersionId, String response, String studentToken)
            throws Exception {
        String value = response == null ? "null" : "\"" + response + "\"";
        mockMvc.perform(put("/api/v1/attempts/" + attemptId + "/responses/" + questionVersionId)
                        .header(AUTHORIZATION, bearer(studentToken)).contentType(JSON)
                        .content("{\"response\":" + value + ",\"timeSpentSeconds\":5,\"markedForReview\":false,"
                                + "\"visited\":true}"))
                .andExpect(status().isOk());
    }

    protected void submit(UUID attemptId, String studentToken) throws Exception {
        mockMvc.perform(post("/api/v1/attempts/" + attemptId + "/submit")
                        .header(AUTHORIZATION, bearer(studentToken)).contentType(JSON).content("{\"confirmed\":true}"))
                .andExpect(status().isOk());
    }

    protected String evaluate(UUID attemptId, String answerKeyId) throws Exception {
        String body = mockMvc.perform(post("/api/v1/attempts/" + attemptId + "/evaluate")
                        .header(AUTHORIZATION, bearer(adminToken())).contentType(JSON)
                        .content("{\"answerKeyId\":\"" + answerKeyId + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.result.id");
    }

    protected void publishResult(String resultId) throws Exception {
        mockMvc.perform(post("/api/v1/results/" + resultId + "/publish")
                        .header(AUTHORIZATION, bearer(adminToken())))
                .andExpect(status().isOk());
    }
}
