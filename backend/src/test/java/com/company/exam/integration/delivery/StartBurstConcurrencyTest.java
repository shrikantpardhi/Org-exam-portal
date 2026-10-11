package com.company.exam.integration.delivery;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.exam.delivery.application.DeliveryDtos;
import com.company.exam.delivery.application.StartAssessmentService;
import com.company.exam.identity.entity.User;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.IntFunction;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * P5-27/P5-28 and the fourth critical verification
 * ({@code specs/14-P5-Delivery-V1.5.md §27, §106, §110, §140, §161}): the concurrent Start burst is
 * race-safe — exactly one logical start per attempt, one session per attempt, and zero snapshot
 * inserts by Start Exam under contention.
 */
class StartBurstConcurrencyTest extends AbstractDeliveryIntegrationTest {

    private static final int BURST = 16;

    @Autowired
    private StartAssessmentService startAssessmentService;

    @Test
    void concurrentStartsForTheSameAttemptProduceOneLogicalStartAndOneSession() throws Exception {
        String versionId = publishAssessmentVersion(2);
        String scheduleId = createSchedule(versionId, "EXAM");
        User student = newStudent("p5-burst-same");
        prepareAttempts(scheduleId);
        UUID schedule = UUID.fromString(scheduleId);
        UUID attemptId = attemptIdFor(schedule, student.getId());
        long questionsBefore = countAttemptQuestions(attemptId);

        List<DeliveryDtos.StartResponse> responses = runConcurrently(BURST,
                index -> startAssessmentService.start(schedule, student.getId()));

        assertThat(responses).hasSize(BURST);
        assertThat(responses).allSatisfy(response -> assertThat(response.attemptId()).isEqualTo(attemptId));
        assertThat(responses.stream().map(DeliveryDtos.StartResponse::sessionId).distinct()).hasSize(1);

        assertThat(countSessions(attemptId)).as("one session per attempt").isEqualTo(1);
        assertThat(countAttemptQuestions(attemptId)).as("no snapshot inserts by Start Exam")
                .isEqualTo(questionsBefore);
    }

    @Test
    void concurrentStartBurstAcrossManyUsersIsRaceSafeAndInsertFree() throws Exception {
        int users = 10;
        String versionId = publishAssessmentVersion(2);
        String scheduleId = createSchedule(versionId, "EXAM");
        UUID schedule = UUID.fromString(scheduleId);

        List<User> students = new ArrayList<>();
        for (int i = 0; i < users; i++) {
            students.add(newStudent("p5-burst-" + i));
        }
        prepareAttempts(scheduleId);

        long preparedAttempts = jdbcTemplate.queryForObject(
                "select count(*) from student_attempt where assessment_schedule_id = ?", Long.class, schedule);
        long snapshotRowsBefore = snapshotRows(schedule);
        assertThat(snapshotRowsBefore).as("two questions per prepared attempt")
                .isEqualTo(preparedAttempts * 2L);
        assertThat(preparedAttempts).isGreaterThanOrEqualTo(users);

        List<DeliveryDtos.StartResponse> responses = runConcurrently(users,
                index -> startAssessmentService.start(schedule, students.get(index).getId()));

        assertThat(responses).hasSize(users);
        assertThat(responses.stream().map(DeliveryDtos.StartResponse::attemptId).distinct()).hasSize(users);
        assertThat(snapshotRows(schedule)).as("Start Exam inserts no snapshot rows under burst")
                .isEqualTo(snapshotRowsBefore);

        for (User student : students) {
            UUID attemptId = attemptIdFor(schedule, student.getId());
            assertThat(countSessions(attemptId)).as("one session per attempt").isEqualTo(1);
            assertThat(countAttemptQuestions(attemptId)).isEqualTo(2);
        }
    }

    private long snapshotRows(UUID scheduleId) {
        Long rows = jdbcTemplate.queryForObject("""
                select count(*) from attempt_question q
                join student_attempt a on q.attempt_id = a.id
                where a.assessment_schedule_id = ?
                """, Long.class, scheduleId);
        return rows == null ? 0 : rows;
    }

    private List<DeliveryDtos.StartResponse> runConcurrently(int concurrency,
            IntFunction<DeliveryDtos.StartResponse> task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(concurrency);
        CountDownLatch ready = new CountDownLatch(concurrency);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<DeliveryDtos.StartResponse>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < concurrency; i++) {
                int index = i;
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    return task.apply(index);
                }));
            }
            assertThat(ready.await(30, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            List<DeliveryDtos.StartResponse> results = new ArrayList<>();
            for (Future<DeliveryDtos.StartResponse> future : futures) {
                results.add(future.get(60, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }
}
