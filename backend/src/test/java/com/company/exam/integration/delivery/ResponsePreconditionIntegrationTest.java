package com.company.exam.integration.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.exam.identity.entity.User;
import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

/**
 * P5-17/P5-18 and the second critical verification
 * ({@code specs/14-P5-Delivery-V1.5.md §42-§47, §161}): response save is an idempotent UPSERT and a
 * stale {@code If-Match} returns 412 {@code STALE_RESPONSE}.
 */
class ResponsePreconditionIntegrationTest extends AbstractDeliveryIntegrationTest {

    @Test
    void staleWriteIsRejectedWith412AndAnAcceptedSaveReturnsANewEtag() throws Exception {
        Started started = start();
        String questionVersionId = started.questionVersionId();
        String base = "/api/v1/attempts/" + started.attemptId() + "/responses/" + questionVersionId;

        // First save: no If-Match (first save is accepted).
        String firstEtag = mockMvc.perform(put(base).header(AUTHORIZATION, bearer(started.token()))
                        .contentType(JSON).content("{\"response\":\"A\",\"timeSpentSeconds\":5,"
                                + "\"markedForReview\":false,\"visited\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.response").value("A"))
                .andReturn().getResponse().getHeader(HttpHeaders.ETAG);
        assertThat(firstEtag).isNotBlank();

        // GET returns the ETag with Cache-Control: no-store.
        mockMvc.perform(get(base).header(AUTHORIZATION, bearer(started.token())))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .exists(HttpHeaders.ETAG));

        // Accepted save with the current ETag returns a new ETag.
        String secondEtag = mockMvc.perform(put(base).header(AUTHORIZATION, bearer(started.token()))
                        .header(HttpHeaders.IF_MATCH, firstEtag).contentType(JSON)
                        .content("{\"response\":\"B\",\"timeSpentSeconds\":7,\"markedForReview\":true,"
                                + "\"visited\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.response").value("B"))
                .andReturn().getResponse().getHeader(HttpHeaders.ETAG);
        assertThat(secondEtag).isNotNull();

        // A delayed request carrying the now-stale ETag must never overwrite the newer response.
        mockMvc.perform(put(base).header(AUTHORIZATION, bearer(started.token()))
                        .header(HttpHeaders.IF_MATCH, firstEtag).contentType(JSON)
                        .content("{\"response\":\"A\",\"timeSpentSeconds\":9,\"markedForReview\":false,"
                                + "\"visited\":true}"))
                .andExpect(status().isPreconditionFailed())
                .andExpect(jsonPath("$.errors[0].code").value("STALE_RESPONSE"));

        // The stored response is still the newer, accepted value.
        mockMvc.perform(get(base).header(AUTHORIZATION, bearer(started.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.response").value("B"));
    }

    @Test
    void ifMatchStarRequiresAnExistingRow() throws Exception {
        Started started = start();
        String base = "/api/v1/attempts/" + started.attemptId() + "/responses/" + started.questionVersionId();
        mockMvc.perform(put(base).header(AUTHORIZATION, bearer(started.token()))
                        .header(HttpHeaders.IF_MATCH, "*").contentType(JSON)
                        .content("{\"response\":\"A\",\"timeSpentSeconds\":1,\"markedForReview\":false,"
                                + "\"visited\":true}"))
                .andExpect(status().isPreconditionFailed())
                .andExpect(jsonPath("$.errors[0].code").value("STALE_RESPONSE"));
    }

    @Test
    void savingTwiceForTheSameQuestionUpdatesRatherThanDuplicates() throws Exception {
        Started started = start();
        String base = "/api/v1/attempts/" + started.attemptId() + "/responses/" + started.questionVersionId();
        mockMvc.perform(put(base).header(AUTHORIZATION, bearer(started.token())).contentType(JSON)
                .content("{\"response\":\"A\",\"timeSpentSeconds\":1,\"markedForReview\":false,\"visited\":true}"))
                .andExpect(status().isOk());
        mockMvc.perform(put(base).header(AUTHORIZATION, bearer(started.token())).contentType(JSON)
                .content("{\"response\":\"B\",\"timeSpentSeconds\":2,\"markedForReview\":false,\"visited\":true}"))
                .andExpect(status().isOk());

        Long rows = jdbcTemplate.queryForObject(
                "select count(*) from student_response where attempt_id = ?", Long.class, started.attemptId());
        assertThat(rows).isEqualTo(1);
    }

    private record Started(UUID attemptId, String token, String questionVersionId) {
    }

    private Started start() throws Exception {
        String versionId = publishAssessmentVersion(1);
        String scheduleId = createSchedule(versionId, "EXAM");
        User student = newStudent("p5-response");
        prepareAttempts(scheduleId);
        UUID attemptId = attemptIdFor(UUID.fromString(scheduleId), student.getId());
        String token = tokenFor(student);
        mockMvc.perform(post("/api/v1/assessment-schedules/" + scheduleId + "/start")
                .header(AUTHORIZATION, bearer(token))).andExpect(status().isOk());
        String questions = mockMvc.perform(get("/api/v1/attempts/" + attemptId + "/questions")
                        .header(AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String questionVersionId = JsonPath.read(questions, "$.data[0].questionVersionId");
        return new Started(attemptId, token, questionVersionId);
    }
}
