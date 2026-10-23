package com.company.exam.integration.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.exam.identity.entity.User;
import com.company.exam.support.QueryCountInspector;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Proves GAP-156: one {@code GET /attempts/{id}/questions} for a 100-question assessment reads a
 * bounded number of rows regardless of question count.
 *
 * <p>Before the fix the endpoint called {@code ContentCatalog.runtimeQuestion} once per question,
 * costing four queries per question -- 309 statements measured for 100 questions. The gate below
 * asserts a flat bound, so a regression that reintroduces a per-question read fails here even if the
 * payload stays correct.
 */
class AttemptQuestionsQueryBudgetIntegrationTest extends AbstractDeliveryIntegrationTest {

    private static final int QUESTIONS = 100;

    /**
     * The ceiling. Measured cost is 10 statements: session, attempt, schedule, assessment version,
     * section names, delivery policy, snapshot rows, snapshot options, question versions with their
     * template configuration, and content options. The bound is tight on purpose -- a per-question
     * read would blow past it by an order of magnitude.
     */
    private static final int MAX_STATEMENTS = 10;

    @Test
    void aHundredQuestionReadIssuesABoundedNumberOfStatements() throws Exception {
        Started started = startAttemptWith("gap156-budget");

        // Warm once: the first read may prepare statements lazily, which is not the steady-state
        // cost this gate is about.
        mockMvc.perform(get("/api/v1/attempts/" + started.attemptId() + "/questions")
                .header(AUTHORIZATION, bearer(started.token()))).andExpect(status().isOk());

        QueryCountInspector.start();
        MvcResult result = mockMvc.perform(get("/api/v1/attempts/" + started.attemptId() + "/questions")
                        .header(AUTHORIZATION, bearer(started.token())))
                .andExpect(status().isOk()).andReturn();
        List<String> statements = QueryCountInspector.drain();

        Integer questionCount = JsonPath.read(result.getResponse().getContentAsString(), "$.data.length()");
        assertThat(questionCount).isEqualTo(QUESTIONS);
        assertThat(statements)
                .as("statements issued for a %d-question read", QUESTIONS)
                .hasSizeLessThanOrEqualTo(MAX_STATEMENTS);

        // The cost must be flat, not merely under the ceiling: doubling the question count must not
        // add statements. This is what distinguishes a batch from a slightly cheaper N+1.
        long statementsForOne = statements.stream()
                .filter(sql -> sql.contains("from question_version")).count();
        assertThat(statementsForOne)
                .as("question_version reads must be batched, not repeated per question")
                .isEqualTo(1);
    }

    @Test
    void theBatchDoesNotReintroduceAPerQuestionRead() throws Exception {
        // A smaller attempt must cost the same number of statements as the 100-question one. If the
        // catalog read were still per-question, the two counts would differ by ~99.
        Started hundred = startAttemptWith("gap156-flat-100");
        int hundredCount = statementCountFor(hundred);

        Started ten = startAttempt("gap156-flat-10", 10);
        int tenCount = statementCountFor(ten);

        assertThat(tenCount)
                .as("a 10-question read must cost no fewer statements than a 100-question read")
                .isEqualTo(hundredCount);
    }

    private int statementCountFor(Started started) throws Exception {
        mockMvc.perform(get("/api/v1/attempts/" + started.attemptId() + "/questions")
                .header(AUTHORIZATION, bearer(started.token()))).andExpect(status().isOk());
        QueryCountInspector.start();
        mockMvc.perform(get("/api/v1/attempts/" + started.attemptId() + "/questions")
                        .header(AUTHORIZATION, bearer(started.token())))
                .andExpect(status().isOk()).andReturn();
        return QueryCountInspector.drain().size();
    }

    private Started startAttemptWith(String prefix) throws Exception {
        return startAttempt(prefix, QUESTIONS);
    }

    private Started startAttempt(String prefix, int questionCount) throws Exception {
        User student = newStudent(prefix);
        String versionId = publishAssessmentVersion(questionCount);
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
