package com.company.exam.integration.delivery;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.exam.administration.dto.AdministrationDtos;
import com.company.exam.administration.service.BusinessRuleService;
import com.company.exam.delivery.entity.SnapshotStatus;
import com.company.exam.identity.entity.User;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * The bounded retry sweep for FAILED snapshots ({@code specs/14-P5-Delivery-V1.5.md §23}, GAP-157).
 *
 * <p>{@code SnapshotPreparationService.retryFailed(scheduleId)} existed but had no caller: no endpoint
 * and no scheduler. A snapshot that failed without an operator retrying it left the candidate unable
 * to start indefinitely. These tests cover the sweep that closes that hole, and the bounds that keep
 * it finite — cool-down, maximum age, and batch size.
 */
class SnapshotRetrySweepIntegrationTest extends AbstractDeliveryIntegrationTest {

    @Autowired
    private BusinessRuleService businessRuleService;

    /** Prepares the attempt for one specific student and waits until the snapshot is READY. */
    private UUID prepareAttemptFor(String scheduleId, User student) throws Exception {
        prepareAttempts(scheduleId);
        UUID attemptId = attemptIdFor(UUID.fromString(scheduleId), student.getId());
        for (int i = 0; i < 5 && !SnapshotStatus.READY.name().equals(snapshotStatus(attemptId)); i++) {
            snapshotPreparationService.preparePendingBatch(500);
        }
        return attemptId;
    }

    /** Marks an attempt FAILED, as a real materialization failure would. */
    private void failAttempt(UUID attemptId) {
        int updated = jdbcTemplate.update(
                "update student_attempt set snapshot_status = 'FAILED', updated_on = now() where id = ?",
                attemptId);
        assertThat(updated).isEqualTo(1);
    }

    /**
     * Pushes every FAILED attempt outside the retry window so a test only observes its own fixtures.
     *
     * <p>Each test prepares a fresh assessment, but the shared database keeps FAILED attempts from the
     * tests that ran before it. The sweep is global, so those would otherwise be counted as eligible.
     * Ageing them past the maximum age makes them permanently ineligible.
     */
    private void quarantinePreExistingFailures() {
        jdbcTemplate.update("update student_attempt set created_on = ? where snapshot_status = 'FAILED'",
                OffsetDateTime.ofInstant(Instant.now().minus(Duration.ofDays(30)), ZoneOffset.UTC));
    }

    private void ageFailure(UUID attemptId, Duration failedForAgo) {
        // JDBC cannot bind an Instant to TIMESTAMPTZ; bind an explicit offset date-time instead.
        jdbcTemplate.update("update student_attempt set updated_on = ? where id = ?",
                OffsetDateTime.ofInstant(Instant.now().minus(failedForAgo), ZoneOffset.UTC), attemptId);
    }

    private String snapshotStatus(UUID attemptId) {
        return jdbcTemplate.queryForObject("select snapshot_status from student_attempt where id = ?",
                String.class, attemptId);
    }

    @Test
    void aFailedSnapshotIsRetriedOnceItHasCooledDown() throws Exception {
        quarantinePreExistingFailures();
        User student = newStudent("retry-sweep");
        String versionId = publishAssessmentVersion(1);
        String scheduleId = createSchedule(versionId, "EXAM");
        UUID attemptId = prepareAttemptFor(scheduleId, student);
        failAttempt(attemptId);
        assertThat(snapshotStatus(attemptId)).isEqualTo(SnapshotStatus.FAILED.name());

        // Age the failure past the cool-down so it becomes eligible.
        ageFailure(attemptId, Duration.ofMinutes(30));

        int retried = snapshotPreparationService.retryFailedSweep(50, Duration.ofMinutes(10),
                Duration.ofHours(24));

        assertThat(retried).isEqualTo(1);
        // The attempt must come back READY, not merely PENDING: preparation re-ran end to end.
        assertThat(snapshotStatus(attemptId)).isEqualTo(SnapshotStatus.READY.name());
    }

    @Test
    void aFailureInsideTheCoolDownWindowIsNotRetried() throws Exception {
        quarantinePreExistingFailures();
        User student = newStudent("retry-cooldown");
        String versionId = publishAssessmentVersion(1);
        String scheduleId = createSchedule(versionId, "EXAM");
        UUID attemptId = prepareAttemptFor(scheduleId, student);
        failAttempt(attemptId);

        // Still inside the cool-down: retrying immediately would just re-run the same failure.
        int retried = snapshotPreparationService.retryFailedSweep(50, Duration.ofMinutes(10),
                Duration.ofHours(24));

        assertThat(retried).isZero();
        assertThat(snapshotStatus(attemptId)).isEqualTo(SnapshotStatus.FAILED.name());
    }

    @Test
    void anAttemptOlderThanTheMaximumAgeIsLeftFailedForAnOperator() throws Exception {
        quarantinePreExistingFailures();
        User student = newStudent("retry-too-old");
        String versionId = publishAssessmentVersion(1);
        String scheduleId = createSchedule(versionId, "EXAM");
        UUID attemptId = prepareAttemptFor(scheduleId, student);
        failAttempt(attemptId);

        // The schema has no retry counter, so age is the bound that stops an endless retry loop on an
        // attempt that can never be prepared. Simulate an attempt created outside the window.
        jdbcTemplate.update("update student_attempt set created_on = ? where id = ?",
                OffsetDateTime.ofInstant(Instant.now().minus(Duration.ofDays(3)), ZoneOffset.UTC), attemptId);
        ageFailure(attemptId, Duration.ofMinutes(30));

        int retried = snapshotPreparationService.retryFailedSweep(50, Duration.ofMinutes(10),
                Duration.ofHours(24));

        assertThat(retried).isZero();
        assertThat(snapshotStatus(attemptId)).isEqualTo(SnapshotStatus.FAILED.name());
    }

    @Test
    void theSweepIsBoundedByItsBatchLimit() throws Exception {
        quarantinePreExistingFailures();
        User first = newStudent("retry-batch-a");
        String versionId = publishAssessmentVersion(1);
        String scheduleId = createSchedule(versionId, "EXAM");
        UUID firstAttempt = prepareAttemptFor(scheduleId, first);
        User second = newStudent("retry-batch-b");
        // The second student needs an attempt on the same schedule; preparing again enrols them.
        prepareAttempts(scheduleId);
        UUID secondAttempt = attemptIdFor(UUID.fromString(scheduleId), second.getId());

        failAttempt(firstAttempt);
        failAttempt(secondAttempt);
        ageFailure(firstAttempt, Duration.ofMinutes(30));
        ageFailure(secondAttempt, Duration.ofMinutes(30));

        int retried = snapshotPreparationService.retryFailedSweep(1, Duration.ofMinutes(10),
                Duration.ofHours(24));

        // Exactly one attempt is taken per tick, so a mass failure cannot monopolize the worker.
        assertThat(retried).isEqualTo(1);
    }

    @Test
    void theSweepIsOffUnlessTheBusinessRuleEnablesIt() {
        // P11 §9.4: a missing rule yields the documented safe default, which for a sweep that re-runs
        // materialization against real content is "disabled".
        assertThat(businessRuleService.resolve("DELIVERY_SNAPSHOT_RETRY_SWEEP")).isEmpty();
    }

    @Test
    void theBusinessRuleCanEnableAndTuneTheSweep() {
        // Administration owns persistence, Delivery owns semantics (P11 §88). The rule is data only;
        // its key and values must survive the executable-construct rejection (§9.3).
        businessRuleService.create(new AdministrationDtos.CreateBusinessRuleRequest(
                "DELIVERY_SNAPSHOT_RETRY_SWEEP",
                Map.of("enabled", true, "coolDownMinutes", 15, "maxAgeHours", 12, "batchSize", 5)),
                UUID.fromString("00000000-0000-0000-0000-000000000001"));

        var rule = businessRuleService.resolve("DELIVERY_SNAPSHOT_RETRY_SWEEP");
        assertThat(rule).isPresent();
        assertThat(rule.orElseThrow().bool("enabled")).contains(true);
        assertThat(rule.orElseThrow().decimal("coolDownMinutes")).isPresent();
        assertThat(rule.orElseThrow().decimal("maxAgeHours")).isPresent();
        assertThat(rule.orElseThrow().decimal("batchSize")).isPresent();
    }
}
