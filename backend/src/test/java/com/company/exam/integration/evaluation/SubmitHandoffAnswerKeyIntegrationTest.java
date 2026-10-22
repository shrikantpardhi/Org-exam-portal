package com.company.exam.integration.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.exam.identity.entity.User;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * GAP-071: the P5 submit handoff must carry an explicit answer key resolved at submission time
 * ({@code specs/14-P5-Delivery-V1.5.md §67} with {@code specs/15-P6-Evaluation-V1.5.md §25, §26}).
 *
 * <p>These tests submit a real attempt through the HTTP contract and inspect the durable
 * {@code EVALUATION} handoff job the submission enqueues. The evaluation worker is disabled by
 * default, so the job stays queued and its parameters are observable.
 */
class SubmitHandoffAnswerKeyIntegrationTest extends AbstractEvaluationIntegrationTest {

    @Test
    void onePublishedKeyHandoffCarriesThatKey() throws Exception {
        String versionId = publishAssessmentVersion(2);
        String scheduleId = createSchedule(versionId, "EXAM");
        User student = newStudent("gap071-one");
        prepareAttempts(scheduleId);
        UUID attemptId = attemptIdFor(uuid(scheduleId), student.getId());
        String token = tokenFor(student);

        String publishedKeyId = publishAnswerKeyWithOptionA(attemptId, versionId);

        startAttempt(scheduleId, token);
        submit(attemptId, token);

        assertThat(evaluationJobCount(attemptId)).isEqualTo(1);
        assertThat(evaluationJobParameters(attemptId))
                .as("the handoff job carries the resolved answerKeyId and attemptId")
                .contains(publishedKeyId.toString())
                .contains(attemptId.toString());
    }

    @Test
    void multiplePublishedKeysHandoffUsesTheHighestVersion() throws Exception {
        String versionId = publishAssessmentVersion(2);
        String scheduleId = createSchedule(versionId, "EXAM");
        User student = newStudent("gap071-multi");
        prepareAttempts(scheduleId);
        UUID attemptId = attemptIdFor(uuid(scheduleId), student.getId());
        String token = tokenFor(student);

        String firstKeyId = publishAnswerKeyWithOptionA(attemptId, versionId);
        String secondKeyId = publishAnswerKeyWithOptionA(attemptId, versionId);

        startAttempt(scheduleId, token);
        submit(attemptId, token);

        assertThat(evaluationJobCount(attemptId)).isEqualTo(1);
        assertThat(evaluationJobParameters(attemptId))
                .as("the highest published version (v2) is carried, not the first")
                .contains(secondKeyId.toString())
                .doesNotContain(firstKeyId.toString());
    }

    @Test
    void noPublishedKeyEnqueuesNoEvaluationJob() throws Exception {
        String versionId = publishAssessmentVersion(2);
        String scheduleId = createSchedule(versionId, "EXAM");
        User student = newStudent("gap071-none");
        prepareAttempts(scheduleId);
        UUID attemptId = attemptIdFor(uuid(scheduleId), student.getId());
        String token = tokenFor(student);

        startAttempt(scheduleId, token);
        submit(attemptId, token);

        assertThat(evaluationJobCount(attemptId))
                .as("without a published answer key, no evaluation job is enqueued")
                .isZero();
        // The attempt is still finalized as SUBMITTED; scoring is simply deferred to the explicit path.
        Long submitted = jdbcTemplate.queryForObject(
                "select count(*) from student_attempt where id = ? and attempt_status = 'SUBMITTED'",
                Long.class, attemptId);
        assertThat(submitted).isEqualTo(1);
    }

    // ------------------------------------------------------------------ helpers

    private static UUID uuid(String value) {
        return UUID.fromString(value);
    }

    private long evaluationJobCount(UUID attemptId) {
        Long count = jdbcTemplate.queryForObject(
                "select count(*) from background_job where job_type = 'EVALUATION' and reference_id = ?",
                Long.class, attemptId);
        return count == null ? 0 : count;
    }

    private String evaluationJobParameters(UUID attemptId) {
        return jdbcTemplate.queryForObject(
                "select parameters from background_job where job_type = 'EVALUATION' and reference_id = ?",
                String.class, attemptId);
    }
}
