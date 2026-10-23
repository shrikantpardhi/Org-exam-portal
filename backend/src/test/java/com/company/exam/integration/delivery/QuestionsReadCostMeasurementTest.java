package com.company.exam.integration.delivery;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.exam.identity.entity.User;
import com.company.exam.support.QueryCountInspector;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Measures the read cost of {@code GET /attempts/{id}/questions} for GAP-156.
 *
 * <p>Counting uses the shared {@link QueryCountInspector}, the same mechanism the regression gate
 * asserts on, so the number recorded here and the number enforced there cannot silently disagree.
 *
 * <p>Prints only; asserts nothing. Kept separate from the gate so the evidence can be regenerated
 * on demand while the gate stays a fast pass/fail. It is disabled by default because it publishes a
 * 100-question assessment and warms the read, which costs about 40 seconds -- too much to pay on
 * every build for a number that only changes when the read path changes.
 */
@org.junit.jupiter.api.Disabled("Measurement harness, not a gate. Remove this annotation to regenerate "
        + "the GAP-156 before/after numbers; the enforced ceiling lives in "
        + "AttemptQuestionsQueryBudgetIntegrationTest.")
class QuestionsReadCostMeasurementTest extends AbstractDeliveryIntegrationTest {

    private static final int QUESTIONS = 100;

    @Test
    void measure() throws Exception {
        User student = newStudent("gap156-measure");
        String versionId = publishAssessmentVersion(QUESTIONS);
        String scheduleId = createSchedule(versionId, "EXAM");
        prepareAttempts(scheduleId);
        UUID attemptId = attemptIdFor(UUID.fromString(scheduleId), student.getId());
        String token = tokenFor(student);
        mockMvc.perform(post("/api/v1/assessment-schedules/" + scheduleId + "/start")
                        .header(AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk());

        // Warm the read once so the measurement reports steady-state cost rather than first-call
        // JIT and connection-pool effects.
        mockMvc.perform(get("/api/v1/attempts/" + attemptId + "/questions")
                .header(AUTHORIZATION, bearer(token))).andExpect(status().isOk());

        QueryCountInspector.start();
        long started = System.nanoTime();
        MvcResult result = mockMvc.perform(get("/api/v1/attempts/" + attemptId + "/questions")
                        .header(AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk()).andReturn();
        long elapsedMs = Math.round((System.nanoTime() - started) / 1_000_000.0);
        List<String> statements = QueryCountInspector.drain();

        int questionCount = com.jayway.jsonpath.JsonPath.read(
                result.getResponse().getContentAsString(), "$.data.length()");

        System.out.println("GAP156_MEASURE questions=" + questionCount
                + " statements=" + statements.size()
                + " elapsedMs=" + elapsedMs);
        for (int i = 0; i < statements.size(); i++) {
            String sql = statements.get(i).replaceAll("\\s+", " ");
            System.out.println("  SQL[" + i + "]: " + sql.substring(0, Math.min(110, sql.length())));
        }
    }
}
