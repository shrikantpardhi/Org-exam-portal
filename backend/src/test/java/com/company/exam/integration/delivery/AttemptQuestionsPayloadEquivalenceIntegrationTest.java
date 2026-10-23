package com.company.exam.integration.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.exam.identity.entity.User;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Proves the GAP-156 batching changed cost only, not the payload.
 *
 * <p>The batched catalog read assembles the same {@code AttemptQuestionView} from different queries,
 * so the risk is a silent field regression: a lost section name, reordered options, a lost
 * {@code requiresLatexParsing}, or content rendered from the wrong version. Each assertion below
 * compares the response against the database directly rather than against a captured fixture, so it
 * cannot pass by agreeing with a snapshot of the bug.
 */
class AttemptQuestionsPayloadEquivalenceIntegrationTest extends AbstractDeliveryIntegrationTest {

    private static final int QUESTIONS = 100;

    @Test
    void theBatchPreservesEveryFieldOfTheFrozenView() throws Exception {
        Started started = startAttempt("gap156-payload");
        List<Map<String, Object>> views = questions(started);

        assertThat(views).hasSize(QUESTIONS);

        // Snapshot order: the view must follow attempt_question.display_order, never version order.
        List<Integer> displayOrders = JsonPath.read(json(started), "$.data[*].displayOrder");
        assertThat(displayOrders).isSorted();

        // Every field the batched path assembles must match its authoritative column.
        for (Map<String, Object> view : views) {
            UUID versionId = UUID.fromString((String) view.get("questionVersionId"));
            UUID attemptQuestionId = UUID.fromString((String) view.get("attemptQuestionId"));

            Map<String, Object> expected = jdbcTemplate.queryForMap("""
                    select q.display_order, q.section_name, q.marks, q.negative_marks, q.question_version_id
                    from attempt_question q where q.id = ?
                    """, attemptQuestionId);

            assertThat(view.get("displayOrder")).isEqualTo(expected.get("display_order"));
            assertThat(view.get("sectionName")).isEqualTo(expected.get("section_name"));
            assertThat(new java.math.BigDecimal(view.get("marks").toString()))
                    .isEqualByComparingTo(new java.math.BigDecimal(expected.get("marks").toString()));
            assertThat(new java.math.BigDecimal(view.get("negativeMarks").toString()))
                    .isEqualByComparingTo(new java.math.BigDecimal(expected.get("negative_marks").toString()));
            assertThat(versionId).isEqualTo(expected.get("question_version_id"));

            // Content is rendered from the exact captured version, and the format is the frozen one.
            String canonicalContent = jdbcTemplate.queryForObject(
                    "select content from question_version where id = ?", String.class, versionId);
            assertThat(view.get("content")).isEqualTo(canonicalContent);
            assertThat(view.get("contentFormat")).isEqualTo("MARKDOWN_LATEX_V1");
            assertThat(view.get("renderedContent")).asString().isNotBlank();
            assertThat(view.get("requiresLatexParsing")).isNotNull();

            // Options keep display_order and carry no correct-answer flag.
            List<Map<String, Object>> options = (List<Map<String, Object>>) view.get("options");
            assertThat(options).isNotEmpty();
            List<Integer> optionOrders = options.stream()
                    .map(option -> (Integer) option.get("displayOrder")).toList();
            assertThat(optionOrders).isSorted();
            for (Map<String, Object> option : options) {
                assertThat(option).doesNotContainKey("isCorrect");
                assertThat(option).doesNotContainKey("correct");
                assertThat(option.get("optionKey")).isNotNull();
                assertThat(option.get("content")).asString().isNotBlank();
                assertThat(option.get("renderedContent")).asString().isNotBlank();
            }
        }
    }

    @Test
    void theBatchReturnsTheSameAnswerForARepeatedRead() throws Exception {
        // Rendering is deterministic for immutable content, so two reads must agree byte for byte.
        // A cache introduced without a renderer-version key would surface here as drift.
        Started started = startAttempt("gap156-stable");
        String first = json(started);
        String second = json(started);
        assertThat(second).isEqualTo(first);
    }

    @Test
    void theBatchedReadAndTheSingleQuestionReadAgree() throws Exception {
        // The batch path and the single-question path are separate code paths now; they must not
        // diverge. This is the regression a future change to either side is most likely to cause.
        Started started = startAttempt("gap156-parity");
        List<Map<String, Object>> batch = questions(started);

        UUID firstVersionId = UUID.fromString((String) batch.get(0).get("questionVersionId"));
        MvcResult single = mockMvc.perform(get("/api/v1/attempts/" + started.attemptId()
                        + "/questions/" + firstVersionId)
                        .header(AUTHORIZATION, bearer(started.token())))
                .andExpect(status().isOk()).andReturn();
        Map<String, Object> singleView = JsonPath.read(single.getResponse().getContentAsString(), "$.data");

        assertThat(singleView).isEqualTo(batch.get(0));
    }

    @Test
    void theBatchedReadStillHidesCorrectAnswersAndAssetPaths() throws Exception {
        Started started = startAttempt("gap156-leak");
        String body = json(started);

        assertThat(body)
                .doesNotContain("isCorrect")
                .doesNotContain("is_correct")
                .doesNotContain("answerKey")
                .doesNotContain("answer_key")
                .doesNotContain("correctOption")
                .doesNotContain("storagePath")
                .doesNotContain("filePath");
    }

    @Test
    void anotherStudentsAttemptQuestionsAreStillRefused() throws Exception {
        // Batching must not widen the read: the ownership check is unchanged and must still fire
        // before any content is loaded.
        Started owner = startAttempt("gap156-owner");
        User intruder = newStudent("gap156-intruder");

        mockMvc.perform(get("/api/v1/attempts/" + owner.attemptId() + "/questions")
                        .header(AUTHORIZATION, bearer(tokenFor(intruder))))
                .andExpect(status().isNotFound());
    }

    private String json(Started started) throws Exception {
        return mockMvc.perform(get("/api/v1/attempts/" + started.attemptId() + "/questions")
                        .header(AUTHORIZATION, bearer(started.token())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private List<Map<String, Object>> questions(Started started) throws Exception {
        return JsonPath.read(json(started), "$.data");
    }

    private Started startAttempt(String prefix) throws Exception {
        User student = newStudent(prefix);
        String versionId = publishAssessmentVersion(QUESTIONS);
        String scheduleId = createSchedule(versionId, "EXAM");
        prepareAttempts(scheduleId);
        UUID attemptId = attemptIdFor(UUID.fromString(scheduleId), student.getId());
        String token = tokenFor(student);
        mockMvc.perform(post("/api/v1/assessment-schedules/" + scheduleId + "/start")
                        .header(AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk());
        return new Started(attemptId, token);
    }

    private record Started(UUID attemptId, String token) {
    }
}
