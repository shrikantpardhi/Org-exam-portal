package com.company.exam.integration.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.exam.identity.entity.User;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Answer key CRUD, publication, evaluation, bonus/dropped/override handling, and evaluate-once
 * behaviour through the frozen HTTP contract
 * ({@code specs/15-P6-Evaluation-V1.5.md §28-§30, §44, §45, §92, §170, §171, §177, §178}).
 */
class EvaluationApiIntegrationTest extends AbstractEvaluationIntegrationTest {

    @Test
    void evaluatesCorrectIncorrectAndUnansweredWithNegativeMarking() throws Exception {
        String versionId = publishAssessmentVersion(3);
        String scheduleId = createSchedule(versionId, "EXAM");
        User student = newStudent("p6-mixed");
        String token = tokenFor(student);
        prepareAttempts(scheduleId);
        UUID attemptId = startAttempt(scheduleId, token);
        List<UUID> questions = snapshotQuestionVersionIds(attemptId);

        answer(attemptId, questions.get(0), "A", token);   // correct
        answer(attemptId, questions.get(1), "B", token);   // incorrect
        answer(attemptId, questions.get(2), null, token);  // unanswered
        submit(attemptId, token);

        String keyId = createAnswerKey(versionId);
        addDetail(keyId, questions.get(0), "NORMAL", correctOptionIdFor(questions.get(0)), null, null);
        addDetail(keyId, questions.get(1), "NORMAL", correctOptionIdFor(questions.get(1)), null,
                new BigDecimal("0.50"));
        addDetail(keyId, questions.get(2), "NORMAL", correctOptionIdFor(questions.get(2)), null, null);
        publishAnswerKey(keyId);

        // 2.00 correct - 0.50 incorrect + 0 unanswered = 1.50 out of a published total of 6.00
        String resultId = evaluate(attemptId, keyId);
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/v1/results/" + resultId).header(AUTHORIZATION, bearer(adminToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.obtainedMarks").value(1.50))
                .andExpect(jsonPath("$.data.percentage").value(25.00))
                .andExpect(jsonPath("$.data.resultStatus").value("CALCULATED"));
    }

    @Test
    void bonusAndDroppedOutcomesContributeAsSpecified() throws Exception {
        String versionId = publishAssessmentVersion(3);
        String scheduleId = createSchedule(versionId, "EXAM");
        User student = newStudent("p6-bonus");
        String token = tokenFor(student);
        prepareAttempts(scheduleId);
        UUID attemptId = startAttempt(scheduleId, token);
        List<UUID> questions = snapshotQuestionVersionIds(attemptId);

        answer(attemptId, questions.get(0), "B", token);  // ignored (BONUS)
        answer(attemptId, questions.get(1), "B", token);  // ignored (DROPPED)
        answer(attemptId, questions.get(2), "A", token);  // correct
        submit(attemptId, token);

        String keyId = createAnswerKey(versionId);
        addDetail(keyId, questions.get(0), "BONUS", null, new BigDecimal("2.00"), null);
        addDetail(keyId, questions.get(1), "DROPPED", null, null, null);
        addDetail(keyId, questions.get(2), "NORMAL", correctOptionIdFor(questions.get(2)), null, null);
        publishAnswerKey(keyId);

        // 2.00 bonus + 0 dropped + 2.00 correct = 4.00 out of 6.00
        String resultId = evaluate(attemptId, keyId);
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/v1/results/" + resultId).header(AUTHORIZATION, bearer(adminToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.obtainedMarks").value(4.00))
                .andExpect(jsonPath("$.data.percentage").value(66.67));
    }

    @Test
    void overrideMarksSupersedeTheAttemptSnapshot() throws Exception {
        String versionId = publishAssessmentVersion(1);
        String scheduleId = createSchedule(versionId, "EXAM");
        User student = newStudent("p6-override");
        String token = tokenFor(student);
        prepareAttempts(scheduleId);
        UUID attemptId = startAttempt(scheduleId, token);
        UUID questionVersionId = snapshotQuestionVersionIds(attemptId).get(0);

        answer(attemptId, questionVersionId, "A", token);
        submit(attemptId, token);

        String keyId = createAnswerKey(versionId);
        addDetail(keyId, questionVersionId, "NORMAL", correctOptionIdFor(questionVersionId),
                new BigDecimal("3.50"), null);
        publishAnswerKey(keyId);

        String resultId = evaluate(attemptId, keyId);
        // override 3.50 supersedes the snapshot 2.00
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/v1/results/" + resultId).header(AUTHORIZATION, bearer(adminToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.obtainedMarks").value(3.50));
    }

    @Test
    void repeatedEvaluationReturnsTheExistingEvaluationAndCreatesNoSecondRow() throws Exception {
        String versionId = publishAssessmentVersion(1);
        String scheduleId = createSchedule(versionId, "EXAM");
        User student = newStudent("p6-idempotent");
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

        assertThat(JsonPath.read(second, "$.data.id").toString())
                .isEqualTo(JsonPath.read(first, "$.data.id").toString());
        assertThat(evaluationRepository.findByAttemptIdOrderByVersionDesc(attemptId)).hasSize(1);
        assertThat(evaluationRepository.findByAttemptIdAndSupersededByIdIsNull(attemptId)).isPresent();
    }

    @Test
    void publishedAnswerKeyRejectsFurtherMutations() throws Exception {
        String versionId = publishAssessmentVersion(2);
        String scheduleId = createSchedule(versionId, "EXAM");
        User student = newStudent("p6-immutable");
        String token = tokenFor(student);
        prepareAttempts(scheduleId);
        UUID attemptId = startAttempt(scheduleId, token);
        List<UUID> questions = snapshotQuestionVersionIds(attemptId);

        String keyId = createAnswerKey(versionId);
        String detailId = addDetail(keyId, questions.get(0), "NORMAL", correctOptionIdFor(questions.get(0)), null,
                null);
        addDetail(keyId, questions.get(1), "NORMAL", correctOptionIdFor(questions.get(1)), null, null);
        publishAnswerKey(keyId);

        mockMvc.perform(post("/api/v1/answer-keys/" + keyId + "/details")
                        .header(AUTHORIZATION, bearer(adminToken())).contentType(JSON)
                        .content("{\"questionVersionId\":\"" + questions.get(0) + "\",\"questionOutcome\":\"NORMAL\","
                                + "\"correctOptionId\":\"" + correctOptionIdFor(questions.get(0)) + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].code").value("ANSWER_KEY_IMMUTABLE"));

        mockMvc.perform(put("/api/v1/answer-keys/" + keyId + "/details/" + detailId)
                        .header(AUTHORIZATION, bearer(adminToken())).contentType(JSON)
                        .content("{\"questionVersionId\":\"" + questions.get(0) + "\",\"questionOutcome\":\"BONUS\"}"))
                .andExpect(status().isConflict());

        mockMvc.perform(delete("/api/v1/answer-keys/" + keyId + "/details/" + detailId)
                        .header(AUTHORIZATION, bearer(adminToken())))
                .andExpect(status().isConflict());
    }

    @Test
    void publicationRequiresExactlyOneDetailPerQuestion() throws Exception {
        String versionId = publishAssessmentVersion(3);
        String scheduleId = createSchedule(versionId, "EXAM");
        User student = newStudent("p6-incomplete");
        String token = tokenFor(student);
        prepareAttempts(scheduleId);
        UUID attemptId = startAttempt(scheduleId, token);
        List<UUID> questions = snapshotQuestionVersionIds(attemptId);

        String keyId = createAnswerKey(versionId);
        addDetail(keyId, questions.get(0), "NORMAL", correctOptionIdFor(questions.get(0)), null, null);
        // Two details intentionally omitted.

        mockMvc.perform(post("/api/v1/answer-keys/" + keyId + "/publish")
                        .header(AUTHORIZATION, bearer(adminToken())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].code").value("ANSWER_KEY_INCOMPLETE"));
        assertThat(answerKeyRepository.findById(UUID.fromString(keyId)).orElseThrow().isPublished()).isFalse();
    }

    @Test
    void rejectsAnUnpublishedAnswerKey() throws Exception {
        String versionId = publishAssessmentVersion(1);
        String scheduleId = createSchedule(versionId, "EXAM");
        User student = newStudent("p6-unpublished");
        String token = tokenFor(student);
        prepareAttempts(scheduleId);
        UUID attemptId = startAttempt(scheduleId, token);
        UUID questionVersionId = snapshotQuestionVersionIds(attemptId).get(0);
        answer(attemptId, questionVersionId, "A", token);
        submit(attemptId, token);

        String keyId = createAnswerKey(versionId);   // never published
        mockMvc.perform(post("/api/v1/attempts/" + attemptId + "/evaluate")
                        .header(AUTHORIZATION, bearer(adminToken())).contentType(JSON)
                        .content("{\"answerKeyId\":\"" + keyId + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].code").value("ANSWER_KEY_NOT_PUBLISHED"));
    }

    @Test
    void rejectsAnAnswerKeyFromAnotherAssessmentVersion() throws Exception {
        String versionA = publishAssessmentVersion(1);
        String versionB = publishAssessmentVersion(1);
        String scheduleA = createSchedule(versionA, "EXAM");
        User student = newStudent("p6-mismatch");
        String token = tokenFor(student);
        prepareAttempts(scheduleA);
        UUID attemptId = startAttempt(scheduleA, token);
        UUID questionVersionId = snapshotQuestionVersionIds(attemptId).get(0);
        answer(attemptId, questionVersionId, "A", token);
        submit(attemptId, token);

        // Publish a complete key for version B.
        UUID questionB = assessmentQuestionVersionId(versionB);
        String keyB = createAnswerKey(versionB);
        addDetail(keyB, questionB, "NORMAL", correctOptionIdFor(questionB), null, null);
        publishAnswerKey(keyB);

        mockMvc.perform(post("/api/v1/attempts/" + attemptId + "/evaluate")
                        .header(AUTHORIZATION, bearer(adminToken())).contentType(JSON)
                        .content("{\"answerKeyId\":\"" + keyB + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].code").value("ANSWER_KEY_NOT_FOR_ASSESSMENT"));
    }

    @Test
    void rejectsAnAttemptThatIsNotSubmitted() throws Exception {
        String versionId = publishAssessmentVersion(1);
        String scheduleId = createSchedule(versionId, "EXAM");
        User student = newStudent("p6-not-submitted");
        String token = tokenFor(student);
        prepareAttempts(scheduleId);
        UUID attemptId = startAttempt(scheduleId, token);
        UUID questionVersionId = snapshotQuestionVersionIds(attemptId).get(0);
        String keyId = createAnswerKey(versionId);
        addDetail(keyId, questionVersionId, "NORMAL", correctOptionIdFor(questionVersionId), null, null);
        publishAnswerKey(keyId);

        mockMvc.perform(post("/api/v1/attempts/" + attemptId + "/evaluate")
                        .header(AUTHORIZATION, bearer(adminToken())).contentType(JSON)
                        .content("{\"answerKeyId\":\"" + keyId + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].code").value("ATTEMPT_NOT_SUBMITTED"));
    }

    private UUID assessmentQuestionVersionId(String assessmentVersionId) {
        return jdbcTemplate.queryForObject(
                "select question_version_id from assessment_question where assessment_version_id = ?",
                UUID.class, UUID.fromString(assessmentVersionId));
    }
}
