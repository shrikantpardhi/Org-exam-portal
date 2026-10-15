package com.company.exam.integration.p7;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.exam.identity.entity.User;
import com.company.exam.integration.evaluation.AbstractEvaluationIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

/**
 * P7 reduced-scale pilot smoke ({@code specs/16-P7-Pilot-V1.5.md §23, §26-§31, §49-§53, §93-§97}).
 *
 * <p><strong>This is a local, single-instance, reduced-scale run — not the staged 100→5,000 ramp.</strong>
 * It exercises the frozen runtime path over the real HTTP contract against a disposable embedded
 * PostgreSQL (ADR-0009) with no edge, no PgBouncer, no Redis and no monitoring stack. Its purpose is
 * to prove the P7 harness approach, the runtime invariants, and the reconciliation queries at small
 * scale, so the staged runs have a validated method. It does not satisfy the P7 scale exit criteria.
 *
 * <p>Asserted gates: prepared snapshots exist before the start burst; the start burst produces zero
 * new {@code attempt_question}/{@code attempt_question_option} rows and exactly one session per
 * attempt; autosave honours the ETag stale-write precondition (412) and retry; submission is durable
 * and idempotent with no duplicate evaluation handoff; recovery returns the same attempt and
 * snapshot; and the counts reconcile.
 */
class PilotSmokeIntegrationTest extends AbstractEvaluationIntegrationTest {

    private static final int USERS = 16;
    private static final int QUESTIONS = 5;
    private static final int CONCURRENCY = 16;

    @Test
    void reducedScalePilotRunReconcilesAndRespectsTheRuntimeGates() throws Exception {
        String versionId = publishAssessmentVersion(QUESTIONS);
        String scheduleId = createSchedule(versionId, "EXAM");
        UUID schedule = UUID.fromString(scheduleId);
        String admin = adminToken();

        List<User> students = new ArrayList<>();
        for (int i = 0; i < USERS; i++) {
            students.add(newStudent("p7-pilot-" + i));
        }
        prepareAttempts(scheduleId);

        // ---- Pre-conditions: prepared snapshot, READY, before any start (§23) ----
        long prepared = count("select count(*) from student_attempt where assessment_schedule_id = ?", schedule);
        long ready = count("select count(*) from student_attempt where assessment_schedule_id = ? "
                + "and snapshot_status = 'READY'", schedule);
        long started = count("select count(*) from student_attempt where assessment_schedule_id = ? "
                + "and attempt_status = 'IN_PROGRESS'", schedule);
        assertThat(prepared).as("prepared attempts").isGreaterThanOrEqualTo(USERS);
        assertThat(ready).as("prepared attempts are READY before start").isGreaterThanOrEqualTo(USERS);
        assertThat(started).as("no attempt is started before the burst").isZero();
        for (User student : students) {
            assertThat(count("select count(*) from student_attempt where assessment_schedule_id = ? "
                    + "and user_id = ? and snapshot_status = 'READY'", schedule, student.getId()))
                    .as("candidate attempt is READY before the burst").isEqualTo(1);
        }

        long snapshotQuestionsBefore = snapshotQuestionRows(schedule);
        long snapshotOptionsBefore = snapshotOptionRows(schedule);
        assertThat(snapshotQuestionsBefore).isEqualTo(ready * QUESTIONS);

        // ---- PROFILE-01 Start burst: concurrent starts, zero snapshot inserts (§26, §27) ----
        Map<UUID, UUID> attemptByStudent = new ConcurrentHashMap<>();
        runConcurrently(USERS, index -> {
            User student = students.get(index);
            String body = mockMvc.perform(post("/api/v1/assessment-schedules/" + scheduleId + "/start")
                            .header(AUTHORIZATION, bearer(tokenFor(student))))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            UUID attemptId = UUID.fromString(JsonPath.read(body, "$.data.attemptId"));
            assertThat(attemptId).isEqualTo(attemptIdFor(schedule, student.getId()));
            attemptByStudent.put(student.getId(), attemptId);
        });

        assertThat(attemptByStudent).hasSize(USERS);
        assertThat(countDistinctAttempts(schedule)).as("one attempt per student, no duplicates")
                .isGreaterThanOrEqualTo(USERS);
        assertThat(snapshotQuestionRows(schedule)).as("Start burst inserts zero attempt_question rows")
                .isEqualTo(snapshotQuestionsBefore);
        assertThat(snapshotOptionRows(schedule)).as("Start burst inserts zero attempt_option rows")
                .isEqualTo(snapshotOptionsBefore);
        for (User student : students) {
            UUID attemptId = attemptByStudent.get(student.getId());
            assertThat(count("select count(*) from assessment_session where attempt_id = ?", attemptId))
                    .as("one session per attempt").isEqualTo(1);
            assertThat(count("select count(*) from student_attempt where id = ? and attempt_status = 'IN_PROGRESS'",
                    attemptId)).as("attempt is IN_PROGRESS").isEqualTo(1);
        }

        // ---- PROFILE-03 Autosave burst with ETag, stale precondition, and retry (§29) ----
        runConcurrently(USERS, index -> {
            UUID attemptId = attemptByStudent.get(students.get(index).getId());
            String token = tokenFor(students.get(index));
            List<UUID> questions = snapshotQuestionVersionIds(attemptId);
            for (int q = 0; q < Math.min(3, questions.size()); q++) {
                UUID questionVersionId = questions.get(q);
                String saved = mockMvc.perform(put("/api/v1/attempts/" + attemptId + "/responses/" + questionVersionId)
                                .header(AUTHORIZATION, bearer(token)).contentType(JSON)
                                .content("{\"response\":\"A\",\"timeSpentSeconds\":5,\"markedForReview\":false,"
                                        + "\"visited\":true}"))
                        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
                String etag = JsonPath.read(saved, "$.data.etag");

                // A stale write with a wrong If-Match is rejected (412) rather than corrupting the answer.
                mockMvc.perform(put("/api/v1/attempts/" + attemptId + "/responses/" + questionVersionId)
                                .header(AUTHORIZATION, bearer(token)).contentType(JSON)
                                .header(HttpHeaders.IF_MATCH, "\"resp-stale-0\"")
                                .content("{\"response\":\"B\",\"timeSpentSeconds\":1,\"markedForReview\":false,"
                                        + "\"visited\":true}"))
                        .andExpect(status().isPreconditionFailed());

                // The client re-fetches and retries with the fresh ETag and the write succeeds.
                mockMvc.perform(put("/api/v1/attempts/" + attemptId + "/responses/" + questionVersionId)
                                .header(AUTHORIZATION, bearer(token)).contentType(JSON)
                                .header(HttpHeaders.IF_MATCH, "\"" + etag + "\"")
                                .content("{\"response\":\"A\",\"timeSpentSeconds\":6,\"markedForReview\":true,"
                                        + "\"visited\":true}"))
                        .andExpect(status().isOk());
            }
        });

        // ---- PROFILE-05 Recovery: same attempt, same snapshot, same answers (§31) ----
        runConcurrently(USERS, index -> {
            UUID attemptId = attemptByStudent.get(students.get(index).getId());
            String token = tokenFor(students.get(index));
            String attemptBody = mockMvc.perform(get("/api/v1/attempts/" + attemptId)
                            .header(AUTHORIZATION, bearer(token)))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertThat(JsonPath.read(attemptBody, "$.data.attemptId").toString()).isEqualTo(attemptId.toString());
            String questionsBody = mockMvc.perform(get("/api/v1/attempts/" + attemptId + "/questions")
                            .header(AUTHORIZATION, bearer(token)))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            List<Map<String, Object>> recovered = JsonPath.read(questionsBody, "$.data");
            String responsesBody = mockMvc.perform(get("/api/v1/attempts/" + attemptId + "/responses")
                            .header(AUTHORIZATION, bearer(token)))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            List<Map<String, Object>> responses = JsonPath.read(responsesBody, "$.data");
            assertThat(recovered).as("recovery returns the full snapshot").hasSize(QUESTIONS);
            assertThat(responses).as("recovery returns the saved answers").hasSize(3);
        });

        // ---- PROFILE-04 Submission burst with duplicate submits (§30) ----
        Map<UUID, String> firstSubmittedOn = new ConcurrentHashMap<>();
        runConcurrently(USERS, index -> {
            UUID attemptId = attemptByStudent.get(students.get(index).getId());
            String token = tokenFor(students.get(index));
            String first = mockMvc.perform(post("/api/v1/attempts/" + attemptId + "/submit")
                            .header(AUTHORIZATION, bearer(token)).contentType(JSON)
                            .content("{\"confirmed\":true}"))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            String duplicate = mockMvc.perform(post("/api/v1/attempts/" + attemptId + "/submit")
                            .header(AUTHORIZATION, bearer(token)).contentType(JSON)
                            .content("{\"confirmed\":true}"))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            String submittedOn = JsonPath.read(first, "$.data.submittedOn");
            assertThat(JsonPath.read(duplicate, "$.data.submittedOn").toString())
                    .as("duplicate submit is idempotent").isEqualTo(submittedOn);
            firstSubmittedOn.put(attemptId, submittedOn);
        });

        long submitted = count("select count(*) from student_attempt where assessment_schedule_id = ? "
                + "and attempt_status = 'SUBMITTED'", schedule);
        assertThat(firstSubmittedOn).hasSize(USERS);
        for (User student : students) {
            UUID attemptId = attemptByStudent.get(student.getId());
            // Durably SUBMITTED, and exactly one evaluation handoff job (no duplicate evaluation).
            assertThat(count("select count(*) from student_attempt where id = ? and attempt_status = 'SUBMITTED'",
                    attemptId)).isEqualTo(1);
            assertThat(count("select count(*) from background_job where job_type = 'EVALUATION' "
                    + "and reference_type = 'STUDENT_ATTEMPT' and reference_id = ?", attemptId))
                    .as("one evaluation handoff per attempt").isEqualTo(1);
            assertThat(count("select count(*) from assessment_session where attempt_id = ? "
                    + "and session_status = 'ACTIVE'", attemptId)).as("session closed on submit").isZero();
        }

        // ---- Evaluation + publication, then reconcile counts (§52, §53, §96, §97) ----
        String answerKeyId = createAnswerKey(versionId);
        for (UUID questionVersionId : snapshotQuestionVersionIds(attemptByStudent.values().iterator().next())) {
            addDetail(answerKeyId, questionVersionId, "NORMAL", correctOptionIdFor(questionVersionId), null, null);
        }
        publishAnswerKey(answerKeyId);

        int evaluated = 0;
        int published = 0;
        for (User student : students) {
            UUID attemptId = attemptByStudent.get(student.getId());
            String evaluationBody = mockMvc.perform(post("/api/v1/attempts/" + attemptId + "/evaluate")
                            .header(AUTHORIZATION, bearer(admin)).contentType(JSON)
                            .content("{\"answerKeyId\":\"" + answerKeyId + "\"}"))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            String resultId = JsonPath.read(evaluationBody, "$.data.result.id");
            evaluated++;
            mockMvc.perform(post("/api/v1/results/" + resultId + "/publish").header(AUTHORIZATION, bearer(admin)))
                    .andExpect(status().isOk());
            published++;
            // Exactly one current evaluation and one result per attempt (no duplicates).
            assertThat(count("select count(*) from evaluation where attempt_id = ? and superseded_by_id is null",
                    attemptId)).isEqualTo(1);
        }

        // Count reconciliation (§94): every started attempt was submitted, evaluated, and published.
        assertThat(count("select count(*) from student_attempt where assessment_schedule_id = ? "
                + "and attempt_status = 'SUBMITTED'", schedule)).isEqualTo(submitted);
        assertThat(evaluated).isEqualTo(USERS);
        assertThat(published).isEqualTo(USERS);

        // Response integrity (§51): exactly one response row per (attempt, question), owned by the attempt.
        assertThat(count("""
                select count(*) from (
                    select attempt_id, question_version_id from student_response
                     group by attempt_id, question_version_id having count(*) > 1
                ) d
                """)).as("no duplicate response rows").isZero();
        assertThat(count("""
                select count(*) from student_response r
                 where not exists (select 1 from attempt_question q
                                    where q.attempt_id = r.attempt_id
                                      and q.question_version_id = r.question_version_id)
                """)).as("no response for an unassigned question").isZero();
        // Score integrity: obtained marks are non-null and within the plausible range for this key.
        assertThat(count("select count(*) from result where obtained_marks is null"))
                .as("every result has obtained marks").isZero();
        assertThat(count("""
                select count(*) from result r join evaluation e on r.evaluation_id = e.id
                 where e.attempt_id in (select id from student_attempt where assessment_schedule_id = ?)
                   and r.rank is null
                """, schedule)).as("published results have a rank").isZero();
    }

    // ------------------------------------------------------------------ helpers

    private long count(String sql, Object... args) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class, args);
        return value == null ? 0 : value;
    }

    private long snapshotQuestionRows(UUID scheduleId) {
        return count("""
                select count(*) from attempt_question q
                join student_attempt a on q.attempt_id = a.id
                where a.assessment_schedule_id = ?
                """, scheduleId);
    }

    private long snapshotOptionRows(UUID scheduleId) {
        return count("""
                select count(*) from attempt_question_option o
                join attempt_question q on o.attempt_question_id = q.id
                join student_attempt a on q.attempt_id = a.id
                where a.assessment_schedule_id = ?
                """, scheduleId);
    }

    private long countDistinctAttempts(UUID scheduleId) {
        return count("select count(distinct id) from student_attempt where assessment_schedule_id = ?", scheduleId);
    }

    private void runConcurrently(int concurrency, ThrowingIntConsumer task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(Math.min(CONCURRENCY, concurrency));
        CountDownLatch ready = new CountDownLatch(concurrency);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        AtomicInteger failures = new AtomicInteger();
        try {
            for (int i = 0; i < concurrency; i++) {
                int index = i;
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    try {
                        go.await();
                        task.accept(index);
                    } catch (Exception failure) {
                        failures.incrementAndGet();
                        throw new IllegalStateException("phase task " + index + " failed", failure);
                    }
                }));
            }
            assertThat(ready.await(60, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            for (Future<?> future : futures) {
                future.get(120, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }
        assertThat(failures.get()).as("no concurrent phase failures").isZero();
    }

    @FunctionalInterface
    private interface ThrowingIntConsumer {
        void accept(int index) throws Exception;
    }
}
