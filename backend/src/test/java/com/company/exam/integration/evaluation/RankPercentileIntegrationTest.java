package com.company.exam.integration.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.exam.identity.entity.User;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Rank and percentile recomputation over the published population, including ties
 * ({@code specs/15-P6-Evaluation-V1.5.md §35, §36, §37, §104, §105, §138}).
 */
class RankPercentileIntegrationTest extends AbstractEvaluationIntegrationTest {

    @Test
    void competitionRankingAndPercentileAreComputedOverPublishedResults() throws Exception {
        String versionId = publishAssessmentVersion(2);
        String scheduleId = createSchedule(versionId, "EXAM");

        User top = newStudent("p6-rank-top");
        User topTie = newStudent("p6-rank-tie");
        User middle = newStudent("p6-rank-mid");
        User bottom = newStudent("p6-rank-bottom");
        prepareAttempts(scheduleId);

        String keyId = null;
        UUID referenceAttempt = attemptIdFor(UUID.fromString(scheduleId), top.getId());
        keyId = publishAnswerKeyWithOptionA(referenceAttempt, versionId);

        String topResult = score(scheduleId, top, keyId, true, true);
        String tieResult = score(scheduleId, topTie, keyId, true, true);
        String midResult = score(scheduleId, middle, keyId, true, false);
        String bottomResult = score(scheduleId, bottom, keyId, false, false);

        // All four results are now PUBLISHED; ranks and percentiles are final.
        assertThat(rankOf(topResult)).isEqualTo(1);
        assertThat(rankOf(tieResult)).isEqualTo(1);
        assertThat(rankOf(midResult)).isEqualTo(3);
        assertThat(rankOf(bottomResult)).isEqualTo(4);

        // percentile = count(score <= mine) / total: 4/4, 4/4, 2/4, 1/4
        assertThat(percentileOf(topResult)).isEqualByComparingTo("100.0000");
        assertThat(percentileOf(tieResult)).isEqualByComparingTo("100.0000");
        assertThat(percentileOf(midResult)).isEqualByComparingTo("50.0000");
        assertThat(percentileOf(bottomResult)).isEqualByComparingTo("25.0000");

        assertThat(marksOf(topResult)).isEqualByComparingTo("4.00");
        assertThat(marksOf(bottomResult)).isEqualByComparingTo("0.00");
    }

    private String score(String scheduleId, User student, String keyId, boolean firstCorrect,
            boolean secondCorrect) throws Exception {
        String token = tokenFor(student);
        UUID attemptId = startAttempt(scheduleId, token);
        List<UUID> questions = snapshotQuestionVersionIds(attemptId);
        answer(attemptId, questions.get(0), firstCorrect ? "A" : "B", token);
        answer(attemptId, questions.get(1), secondCorrect ? "A" : "B", token);
        submit(attemptId, token);
        String resultId = evaluate(attemptId, keyId);
        publishResult(resultId);
        return resultId;
    }

    private Integer rankOf(String resultId) {
        return jdbcTemplate.queryForObject("select rank from result where id = ?", Integer.class,
                UUID.fromString(resultId));
    }

    private java.math.BigDecimal percentileOf(String resultId) {
        return jdbcTemplate.queryForObject("select percentile from result where id = ?",
                java.math.BigDecimal.class, UUID.fromString(resultId));
    }

    private java.math.BigDecimal marksOf(String resultId) {
        return jdbcTemplate.queryForObject("select obtained_marks from result where id = ?",
                java.math.BigDecimal.class, UUID.fromString(resultId));
    }
}
