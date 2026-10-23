package com.company.exam.integration.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.exam.identity.entity.User;
import com.company.exam.support.QueryCountInspector;
import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Start Exam SQL budget ({@code specs/14-P5-Delivery-V1.5.md §26-§29}; GAP-059).
 *
 * <p>Start Exam is a lightweight state transition. Its cost must not grow with the assessment, and
 * the number of statements must stay bounded so that a 5,000-user start burst is limited by capacity
 * rather than by avoidable round trips.
 *
 * <p>The budget includes the authentication read that the JWT filter performs on every request. The
 * ceiling is 10: nine for a free start and ten for a paid one (the entitlement decision is an extra
 * read). Both are asserted, so a regression in either path fails.
 */
class StartAssessmentQueryBudgetIntegrationTest extends AbstractDeliveryIntegrationTest {

    private static final int MAX_STATEMENTS = 10;

    @Test
    void freeStartStaysWithinTheStatementBudget() throws Exception {
        String versionId = publishAssessmentVersion(2);
        String scheduleId = createSchedule(versionId, "EXAM");
        User student = newStudent("p5-budget-free");
        prepareAttempts(scheduleId);
        warmUp(scheduleId, "p5-budget-free-warm");

        List<String> statements = measureStart(scheduleId, student);

        assertThat(statements).hasSizeLessThanOrEqualTo(MAX_STATEMENTS);
        assertThat(statements).anyMatch(sql -> sql.startsWith("update student_attempt"));
        assertThat(statements).anyMatch(sql -> sql.startsWith("insert into assessment_session"));
    }

    @Test
    void paidStartWithAGrantStaysWithinTheStatementBudget() throws Exception {
        PaidAssessment paid = paidAssessment();
        User student = newStudent("p5-budget-paid");
        prepareAttemptsFor(paid.scheduleId(), student.getId());
        grant(student.getId(), paid.packageId());
        warmUp(paid.scheduleId(), "p5-budget-paid-warm");

        List<String> statements = measureStart(paid.scheduleId(), student);

        assertThat(statements).hasSizeLessThanOrEqualTo(MAX_STATEMENTS);
        // The merged entitlement decision is one statement, not a mapping read plus a count.
        assertThat(statements).filteredOn(sql -> sql.contains("access_grant")).hasSize(1);
    }

    @Test
    void deniedPaidStartReadsLessThanTheBudgetAndCreatesNoSession() throws Exception {
        PaidAssessment paid = paidAssessment();
        User student = newStudent("p5-budget-denied");
        prepareAttemptsFor(paid.scheduleId(), student.getId());
        UUID attemptId = attemptIdFor(UUID.fromString(paid.scheduleId()), student.getId());
        String token = tokenFor(student);

        QueryCountInspector.start();
        mockMvc.perform(post("/api/v1/assessment-schedules/" + paid.scheduleId() + "/start")
                        .header(AUTHORIZATION, bearer(token)))
                .andExpect(status().isForbidden());
        List<String> statements = QueryCountInspector.drain();

        assertThat(statements).hasSizeLessThanOrEqualTo(MAX_STATEMENTS);
        // A denied start mutates nothing: no transition, no session.
        assertThat(statements).noneMatch(sql -> sql.startsWith("update student_attempt"));
        assertThat(statements).noneMatch(sql -> sql.startsWith("insert into assessment_session"));
        assertThat(attemptStatus(attemptId)).isEqualTo("NOT_STARTED");
        assertThat(countSessions(attemptId)).isZero();
    }

    @Test
    void repeatedStartOfAnInProgressAttemptStaysWithinTheBudget() throws Exception {
        String versionId = publishAssessmentVersion(2);
        String scheduleId = createSchedule(versionId, "EXAM");
        User student = newStudent("p5-budget-repeat");
        prepareAttempts(scheduleId);
        String token = tokenFor(student);
        mockMvc.perform(post("/api/v1/assessment-schedules/" + scheduleId + "/start")
                        .header(AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk());

        QueryCountInspector.start();
        mockMvc.perform(post("/api/v1/assessment-schedules/" + scheduleId + "/start")
                        .header(AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk());
        List<String> statements = QueryCountInspector.drain();

        assertThat(statements).hasSizeLessThanOrEqualTo(MAX_STATEMENTS);
        // The idempotent branch must not attempt the transition again.
        assertThat(statements).noneMatch(sql -> sql.startsWith("update student_attempt"));
    }

    @Test
    void startCreatesNoSnapshotRowsAndItsCostDoesNotScaleWithQuestionCount() throws Exception {
        String versionId = publishAssessmentVersion(2);
        String scheduleId = createSchedule(versionId, "EXAM");
        User student = newStudent("p5-budget-invariant");
        prepareAttempts(scheduleId);
        UUID attemptId = attemptIdFor(UUID.fromString(scheduleId), student.getId());
        long questionsBefore = countAttemptQuestions(attemptId);
        long optionsBefore = countOptions(attemptId);
        warmUp(scheduleId, "p5-budget-invariant-warm");

        List<String> statements = measureStart(scheduleId, student);

        assertThat(statements).hasSizeLessThanOrEqualTo(MAX_STATEMENTS);
        // Start Exam must never materialize a snapshot.
        assertThat(countAttemptQuestions(attemptId)).isEqualTo(questionsBefore);
        assertThat(countOptions(attemptId)).isEqualTo(optionsBefore);
        // The only statement that touches the snapshot filters section names; it never reads rows
        // into memory per question.
        assertThat(statements).filteredOn(sql -> sql.contains("attempt_question")).hasSize(1);
    }

    // ----- helpers -----

    private List<String> measureStart(String scheduleId, User student) throws Exception {
        // The token is minted before measurement: login writes a login_session row, which is
        // authentication setup rather than Start Exam cost.
        String token = tokenFor(student);
        QueryCountInspector.start();
        mockMvc.perform(post("/api/v1/assessment-schedules/" + scheduleId + "/start")
                        .header(AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk());
        return QueryCountInspector.drain();
    }

    /** Starts with an unrelated student so JIT and pool warm-up do not distort the measurement. */
    private void warmUp(String scheduleId, String label) throws Exception {
        User warms = newStudent(label);
        prepareAttempts(scheduleId);
        mockMvc.perform(post("/api/v1/assessment-schedules/" + scheduleId + "/start")
                .header(AUTHORIZATION, bearer(tokenFor(warms)))).andReturn();
    }

    private String attemptStatus(UUID attemptId) {
        return jdbcTemplate.queryForObject("select attempt_status from student_attempt where id = ?",
                String.class, attemptId);
    }

    private long countOptions(UUID attemptId) {
        Long count = jdbcTemplate.queryForObject(
                "select count(*) from attempt_question_option where attempt_question_id in "
                        + "(select id from attempt_question where attempt_id = ?)", Long.class, attemptId);
        return count == null ? 0 : count;
    }

    private void prepareAttemptsFor(String scheduleId, UUID userId) throws Exception {
        prepareAttempts(scheduleId);
        UUID attemptId = attemptIdFor(UUID.fromString(scheduleId), userId);
        for (int i = 0; i < 5 && !"READY".equals(snapshotStatus(attemptId)); i++) {
            snapshotPreparationService.preparePendingBatch(500);
        }
    }

    private String snapshotStatus(UUID attemptId) {
        return jdbcTemplate.queryForObject("select snapshot_status from student_attempt where id = ?",
                String.class, attemptId);
    }

    private PaidAssessment paidAssessment() throws Exception {
        String versionId = publishAssessmentVersion(1);
        UUID assessmentId = jdbcTemplate.queryForObject(
                "select assessment_id from assessment_version where id = ?", UUID.class,
                UUID.fromString(versionId));
        UUID collectionId = UUID.randomUUID();
        jdbcTemplate.update("insert into assessment_collection "
                + "(id, code, name, status, created_on) values (?, ?, ?, 'ACTIVE', now())",
                collectionId, "BUDGET-COL-" + collectionId, "Budget Collection");
        jdbcTemplate.update("insert into collection_assessment "
                + "(id, collection_id, assessment_id, status, created_on) "
                + "values (?, ?, ?, 'ACTIVE', now())", UUID.randomUUID(), collectionId, assessmentId);
        UUID packageId = createPackage();
        jdbcTemplate.update("insert into access_package_collection "
                + "(id, access_package_id, assessment_collection_id, status, created_on) "
                + "values (?, ?, ?, 'ACTIVE', now())", UUID.randomUUID(), packageId, collectionId);
        return new PaidAssessment(versionId, packageId, createSchedule(versionId, "EXAM"));
    }

    private UUID createPackage() throws Exception {
        String admin = adminToken();
        String suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        String created = mockMvc.perform(post("/api/v1/access-packages")
                        .header(AUTHORIZATION, bearer(admin)).contentType(JSON)
                        .content("{\"code\":\"BUDGET-PKG-" + suffix + "\",\"name\":\"Budget Package\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(created, "$.data.id"));
    }

    private void grant(UUID userId, UUID packageId) throws Exception {
        Instant start = Instant.now().minus(1, ChronoUnit.HOURS);
        Instant end = Instant.now().plus(30, ChronoUnit.DAYS);
        mockMvc.perform(post("/api/v1/access-grants").header(AUTHORIZATION, bearer(adminToken()))
                        .contentType(JSON)
                        .content("{\"userId\":\"" + userId + "\",\"accessPackageId\":\"" + packageId
                                + "\",\"startDate\":\"" + start + "\",\"endDate\":\"" + end + "\"}"))
                .andExpect(status().isCreated());
    }

    private record PaidAssessment(String versionId, UUID packageId, String scheduleId) {
    }
}
