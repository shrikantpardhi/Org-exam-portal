package com.company.exam.integration.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.exam.identity.entity.User;
import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * The paid-content gate on the Start Exam path (GAP-100)
 * ({@code specs/14-P5-Delivery-V1.5.md §19}; {@code specs/18-P9-Commerce-V1.5.md §3.2, §16, §28.1};
 * ADR-0026).
 *
 * <p>An assessment is free if and only if no AccessPackage grants access to it. A paid assessment
 * requires a currently valid AccessGrant. The denial is HTTP 403 with the controlled error code
 * {@code ACCESS_DENIED_PAID_CONTENT}, decided on the server before the attempt transition.
 */
class EntitlementGateIntegrationTest extends AbstractDeliveryIntegrationTest {

    @Test
    void aStudentWithAValidGrantCanStartAPaidAssessment() throws Exception {
        PaidAssessment paid = paidAssessment();
        User student = newStudent("gate-granted");
        prepareAttemptsFor(paid.scheduleId(), student.getId());
        grant(student.getId(), paid.packageId(), Instant.now().minus(1, ChronoUnit.HOURS),
                Instant.now().plus(30, ChronoUnit.DAYS));

        mockMvc.perform(post("/api/v1/assessment-schedules/" + paid.scheduleId() + "/start")
                        .header(AUTHORIZATION, bearer(tokenFor(student))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.attemptId").exists())
                .andExpect(jsonPath("$.data.assessmentVersionId").value(paid.versionId()));

        UUID attemptId = attemptIdFor(UUID.fromString(paid.scheduleId()), student.getId());
        assertThat(attemptStatus(attemptId)).isEqualTo("IN_PROGRESS");
    }

    @Test
    void aStudentWithoutAGrantIsDeniedWithTheControlledErrorCode() throws Exception {
        PaidAssessment paid = paidAssessment();
        User student = newStudent("gate-denied");
        prepareAttemptsFor(paid.scheduleId(), student.getId());
        UUID attemptId = attemptIdFor(UUID.fromString(paid.scheduleId()), student.getId());

        mockMvc.perform(post("/api/v1/assessment-schedules/" + paid.scheduleId() + "/start")
                        .header(AUTHORIZATION, bearer(tokenFor(student))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errors[0].code").value("ACCESS_DENIED_PAID_CONTENT"));

        // The gate runs before the attempt transition, so a denied start mutates no state.
        assertThat(attemptStatus(attemptId)).isEqualTo("NOT_STARTED");
        assertThat(countSessions(attemptId)).isZero();
    }

    @Test
    void aFreeAssessmentStartsWithoutAGrant() throws Exception {
        // No collection and no package references this assessment, so it is free content.
        String versionId = publishAssessmentVersion(1);
        String scheduleId = createSchedule(versionId, "EXAM");
        User student = newStudent("gate-free");
        prepareAttemptsFor(scheduleId, student.getId());

        mockMvc.perform(post("/api/v1/assessment-schedules/" + scheduleId + "/start")
                        .header(AUTHORIZATION, bearer(tokenFor(student))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.attemptId").exists());
    }

    @Test
    void anAssessmentInAnUnmappedCollectionIsAlsoFree() throws Exception {
        // The assessment sits in a collection, but no AccessPackage maps that collection: still free.
        String versionId = publishAssessmentVersion(1);
        UUID assessmentId = assessmentIdOf(versionId);
        UUID collectionId = UUID.randomUUID();
        insertCollection(collectionId);
        insertCollectionAssessment(collectionId, assessmentId);
        String scheduleId = createSchedule(versionId, "EXAM");
        User student = newStudent("gate-free-collection");
        prepareAttemptsFor(scheduleId, student.getId());

        mockMvc.perform(post("/api/v1/assessment-schedules/" + scheduleId + "/start")
                        .header(AUTHORIZATION, bearer(tokenFor(student))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.attemptId").exists());
    }

    @Test
    void theGateIsServerSideAndCannotBeBypassedFromTheClient() throws Exception {
        PaidAssessment paid = paidAssessment();
        User student = newStudent("gate-bypass");
        prepareAttemptsFor(paid.scheduleId(), student.getId());
        UUID attemptId = attemptIdFor(UUID.fromString(paid.scheduleId()), student.getId());

        // The endpoint takes no access input. Query parameters and a body claiming entitlement are
        // ignored, and the server-side decision still denies the start.
        mockMvc.perform(post("/api/v1/assessment-schedules/" + paid.scheduleId() + "/start")
                        .header(AUTHORIZATION, bearer(tokenFor(student)))
                        .param("bypassEntitlement", "true")
                        .param("accessPackageId", paid.packageId().toString())
                        .contentType(JSON)
                        .content("{\"bypassEntitlement\":true,\"accessPackageId\":\"" + paid.packageId() + "\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errors[0].code").value("ACCESS_DENIED_PAID_CONTENT"));

        assertThat(attemptStatus(attemptId)).isEqualTo("NOT_STARTED");
        assertThat(countSessions(attemptId)).isZero();
    }

    @Test
    void aGrantForAnotherPackageDoesNotUnlockTheAssessment() throws Exception {
        PaidAssessment paid = paidAssessment();
        User student = newStudent("gate-other-package");
        prepareAttemptsFor(paid.scheduleId(), student.getId());

        // A valid grant for a package that does not cover this assessment grants nothing (§9.1).
        UUID unrelatedPackage = createAccessPackage();
        grant(student.getId(), unrelatedPackage, Instant.now().minus(1, ChronoUnit.HOURS),
                Instant.now().plus(30, ChronoUnit.DAYS));

        mockMvc.perform(post("/api/v1/assessment-schedules/" + paid.scheduleId() + "/start")
                        .header(AUTHORIZATION, bearer(tokenFor(student))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errors[0].code").value("ACCESS_DENIED_PAID_CONTENT"));
    }

    @Test
    void anExpiredGrantDoesNotUnlockTheAssessment() throws Exception {
        PaidAssessment paid = paidAssessment();
        User student = newStudent("gate-expired");
        prepareAttemptsFor(paid.scheduleId(), student.getId());

        // The grant ended before the start: the end date is inclusive, so an end in the past denies.
        grant(student.getId(), paid.packageId(), Instant.now().minus(2, ChronoUnit.DAYS),
                Instant.now().minus(1, ChronoUnit.DAYS));

        mockMvc.perform(post("/api/v1/assessment-schedules/" + paid.scheduleId() + "/start")
                        .header(AUTHORIZATION, bearer(tokenFor(student))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errors[0].code").value("ACCESS_DENIED_PAID_CONTENT"));
    }

    @Test
    void aPaidAssessmentScheduledInPracticeModeIsNotEntitlementGated() throws Exception {
        // ADR-0026, Decision 3: PRACTICE schedules are not gated. The mode is server-owned, so this
        // is not a client-side bypass.
        PaidAssessment paid = paidAssessment("PRACTICE");
        User student = newStudent("gate-practice");
        prepareAttemptsFor(paid.scheduleId(), student.getId());

        mockMvc.perform(post("/api/v1/assessment-schedules/" + paid.scheduleId() + "/start")
                        .header(AUTHORIZATION, bearer(tokenFor(student))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mode").value("PRACTICE"));
    }

    // ------------------------------------------------------------------ helpers

    private record PaidAssessment(String versionId, UUID assessmentId, UUID packageId, String scheduleId) {
    }

    /** Publishes an assessment, sells it through a package, and opens an EXAM schedule for it. */
    private PaidAssessment paidAssessment() throws Exception {
        return paidAssessment("EXAM");
    }

    private PaidAssessment paidAssessment(String mode) throws Exception {
        String versionId = publishAssessmentVersion(1);
        UUID assessmentId = assessmentIdOf(versionId);
        UUID collectionId = UUID.randomUUID();
        insertCollection(collectionId);
        insertCollectionAssessment(collectionId, assessmentId);

        String admin = adminToken();
        UUID packageId = createAccessPackage(admin);
        mockMvc.perform(post("/api/v1/access-packages/" + packageId + "/collections")
                        .header(AUTHORIZATION, bearer(admin)).contentType(JSON)
                        .content("{\"collectionId\":\"" + collectionId + "\"}"))
                .andExpect(status().isCreated());

        return new PaidAssessment(versionId, assessmentId, packageId, createSchedule(versionId, mode));
    }

    private UUID createAccessPackage() throws Exception {
        return createAccessPackage(adminToken());
    }

    private UUID createAccessPackage(String admin) throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        String created = mockMvc.perform(post("/api/v1/access-packages").header(AUTHORIZATION, bearer(admin))
                        .contentType(JSON)
                        .content("{\"code\":\"GATE-PKG-" + suffix + "\",\"name\":\"Gate Package\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(created, "$.data.id"));
    }

    private void grant(UUID userId, UUID packageId, Instant start, Instant end) throws Exception {
        mockMvc.perform(post("/api/v1/access-grants").header(AUTHORIZATION, bearer(adminToken()))
                        .contentType(JSON)
                        .content("{\"userId\":\"" + userId + "\",\"accessPackageId\":\"" + packageId
                                + "\",\"startDate\":\"" + start + "\",\"endDate\":\"" + end + "\"}"))
                .andExpect(status().isCreated());
    }

    private UUID assessmentIdOf(String assessmentVersionId) {
        return jdbcTemplate.queryForObject("select assessment_id from assessment_version where id = ?",
                UUID.class, UUID.fromString(assessmentVersionId));
    }

    private void insertCollection(UUID collectionId) {
        jdbcTemplate.update("insert into assessment_collection (id, code, name, status, created_on) "
                + "values (?, ?, ?, 'ACTIVE', now())", collectionId, "GATE-COL-" + collectionId,
                "Gate Collection");
    }

    private void insertCollectionAssessment(UUID collectionId, UUID assessmentId) {
        jdbcTemplate.update("insert into collection_assessment (id, collection_id, assessment_id, status, created_on) "
                + "values (?, ?, ?, 'ACTIVE', now())", UUID.randomUUID(), collectionId, assessmentId);
    }

    /**
     * Prepares the schedule and makes sure this candidate's attempt reached READY. The preparation
     * batch is bounded, so the batch is drained until the candidate's own attempt is prepared.
     */
    private void prepareAttemptsFor(String scheduleId, UUID userId) throws Exception {
        prepareAttempts(scheduleId);
        UUID attemptId = attemptIdFor(UUID.fromString(scheduleId), userId);
        for (int i = 0; i < 5 && !"READY".equals(snapshotStatus(attemptId)); i++) {
            snapshotPreparationService.preparePendingBatch(500);
        }
    }

    private String attemptStatus(UUID attemptId) {
        return jdbcTemplate.queryForObject("select attempt_status from student_attempt where id = ?",
                String.class, attemptId);
    }

    private String snapshotStatus(UUID attemptId) {
        return jdbcTemplate.queryForObject("select snapshot_status from student_attempt where id = ?",
                String.class, attemptId);
    }
}
