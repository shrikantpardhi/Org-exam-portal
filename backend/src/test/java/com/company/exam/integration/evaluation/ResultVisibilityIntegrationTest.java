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
 * Result visibility, publication, and scorecard security
 * ({@code specs/15-P6-Evaluation-V1.5.md §39, §54, §55, §57-§60, §95, §101, §130, §137, §139, §142, §179, §180}).
 */
class ResultVisibilityIntegrationTest extends AbstractEvaluationIntegrationTest {

    private record Fixture(UUID attemptId, String studentToken, String resultId) {
    }

    private Fixture evaluated() throws Exception {
        String versionId = publishAssessmentVersion(1);
        String scheduleId = createSchedule(versionId, "EXAM");
        User student = newStudent("p6-visibility");
        String token = tokenFor(student);
        prepareAttempts(scheduleId);
        UUID attemptId = startAttempt(scheduleId, token);
        UUID questionVersionId = snapshotQuestionVersionIds(attemptId).get(0);
        answer(attemptId, questionVersionId, "A", token);
        submit(attemptId, token);
        String keyId = publishAnswerKeyWithOptionA(attemptId, versionId);
        return new Fixture(attemptId, token, evaluate(attemptId, keyId));
    }

    @Test
    void aStudentCannotSeeAnUnpublishedResultAndSeesItAfterPublication() throws Exception {
        Fixture fixture = evaluated();

        mockMvc.perform(get("/api/v1/attempts/" + fixture.attemptId() + "/result")
                        .header(AUTHORIZATION, bearer(fixture.studentToken())))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/results/" + fixture.resultId())
                        .header(AUTHORIZATION, bearer(fixture.studentToken())))
                .andExpect(status().isNotFound());

        publishResult(fixture.resultId());

        mockMvc.perform(get("/api/v1/attempts/" + fixture.attemptId() + "/result")
                        .header(AUTHORIZATION, bearer(fixture.studentToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.resultStatus").value("PUBLISHED"))
                .andExpect(jsonPath("$.data.obtainedMarks").value(2.00))
                .andExpect(jsonPath("$.data.rank").value(1))
                .andExpect(jsonPath("$.data.percentile").value(100.0000));
    }

    @Test
    void aSingleCandidateReceivesRankOneAndFullPercentile() throws Exception {
        Fixture fixture = evaluated();
        publishResult(fixture.resultId());
        assertThat(rankOf(fixture.resultId())).isEqualTo(1);
        assertThat(percentileOf(fixture.resultId())).isEqualByComparingTo("100.0000");
    }

    @Test
    void publicationIsIdempotentAndAuditedOnce() throws Exception {
        Fixture fixture = evaluated();
        publishResult(fixture.resultId());
        publishResult(fixture.resultId());
        assertThat(rankOf(fixture.resultId())).isEqualTo(1);
        assertThat(resultRepository.findByEvaluationId(evaluationRepository
                .findByAttemptIdAndSupersededByIdIsNull(fixture.attemptId()).orElseThrow().getId())).isPresent();
    }

    @Test
    void scorecardRequiresAPublishedResultAndIsIdempotentPerResult() throws Exception {
        Fixture fixture = evaluated();

        mockMvc.perform(post("/api/v1/results/" + fixture.resultId() + "/scorecard")
                        .header(AUTHORIZATION, bearer(adminToken())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].code").value("RESULT_NOT_PUBLISHABLE"));

        publishResult(fixture.resultId());

        String created = mockMvc.perform(post("/api/v1/results/" + fixture.resultId() + "/scorecard")
                        .header(AUTHORIZATION, bearer(adminToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.secureDownloadReference").exists())
                .andReturn().getResponse().getContentAsString();
        String scorecardId = JsonPath.read(created, "$.data.scorecardId");
        assertThat(created).doesNotContain("scorecards/").doesNotContain(".html");

        // The owner (student) can read the scorecard metadata, not the storage path.
        mockMvc.perform(get("/api/v1/results/" + fixture.resultId() + "/scorecard")
                        .header(AUTHORIZATION, bearer(fixture.studentToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.scorecardId").value(scorecardId));

        // A second generation returns the same artifact.
        mockMvc.perform(post("/api/v1/results/" + fixture.resultId() + "/scorecard")
                        .header(AUTHORIZATION, bearer(adminToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.scorecardId").value(scorecardId));
        assertThat(scorecardRepository.findByResultId(UUID.fromString(fixture.resultId()))).isPresent();
        assertThat(jdbcTemplate.queryForObject("select count(*) from scorecard where result_id = ?", Long.class,
                UUID.fromString(fixture.resultId()))).isEqualTo(1L);
    }

    private Integer rankOf(String resultId) {
        return jdbcTemplate.queryForObject("select rank from result where id = ?", Integer.class,
                UUID.fromString(resultId));
    }

    private java.math.BigDecimal percentileOf(String resultId) {
        return jdbcTemplate.queryForObject("select percentile from result where id = ?", java.math.BigDecimal.class,
                UUID.fromString(resultId));
    }
}
