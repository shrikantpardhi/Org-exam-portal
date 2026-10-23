package com.company.exam.integration.delivery;

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
 * Measures the SQL cost of {@code POST /assessment-schedules/{id}/start} for GAP-059.
 *
 * <p>Prints only; asserts nothing. Disabled by default because it publishes assessments and takes
 * about a minute; the enforced ceiling lives in {@code StartAssessmentQueryBudgetIntegrationTest}.
 */
@org.junit.jupiter.api.Disabled("Measurement harness for GAP-059; remove to regenerate the numbers.")
class StartCostMeasurementTest extends AbstractDeliveryIntegrationTest {

    @Test
    void measureFreeAndPaid() throws Exception {
        measure("free", false);
        measure("paid-granted", true);
    }

    private void measure(String label, boolean paid) throws Exception {
        String versionId = publishAssessmentVersion(1);
        UUID packageId = null;
        if (paid) {
            UUID assessmentId = jdbcTemplate.queryForObject(
                    "select assessment_id from assessment_version where id = ?", UUID.class,
                    UUID.fromString(versionId));
            UUID collectionId = UUID.randomUUID();
            jdbcTemplate.update("insert into assessment_collection "
                    + "(id, code, name, status, created_on) values (?, ?, ?, 'ACTIVE', now())",
                    collectionId, "G059-COL-" + collectionId, "G059 Collection");
            jdbcTemplate.update("insert into collection_assessment "
                    + "(id, collection_id, assessment_id, status, created_on) "
                    + "values (?, ?, ?, 'ACTIVE', now())", UUID.randomUUID(), collectionId, assessmentId);
            packageId = createPackage();
            jdbcTemplate.update("insert into access_package_collection "
                    + "(id, access_package_id, assessment_collection_id, status, created_on) "
                    + "values (?, ?, ?, 'ACTIVE', now())", UUID.randomUUID(), packageId, collectionId);
        }
        String scheduleId = createSchedule(versionId, "EXAM");

        User first = newStudent("gap059-" + label + "-warm");
        prepareAttempts(scheduleId);
        if (paid) {
            grant(first.getId(), packageId);
        }
        mockMvc.perform(post("/api/v1/assessment-schedules/" + scheduleId + "/start")
                .header(AUTHORIZATION, bearer(tokenFor(first)))).andReturn();

        User second = newStudent("gap059-" + label + "-real");
        prepareAttempts(scheduleId);
        String token2 = tokenFor(second);
        if (paid) {
            grant(second.getId(), packageId);
        }

        QueryCountInspector.start();
        long started = System.nanoTime();
        MvcResult result = mockMvc.perform(post("/api/v1/assessment-schedules/" + scheduleId + "/start")
                        .header(AUTHORIZATION, bearer(token2)))
                .andExpect(status().isOk()).andReturn();
        long elapsedMs = Math.round((System.nanoTime() - started) / 1_000_000.0);
        List<String> statements = QueryCountInspector.drain();
        String attemptId = JsonPath.read(result.getResponse().getContentAsString(), "$.data.attemptId");

        System.out.println("GAP059_MEASURE " + label + " statements=" + statements.size()
                + " elapsedMs=" + elapsedMs + " attempt=" + attemptId);
        for (int i = 0; i < statements.size(); i++) {
            String sql = statements.get(i).replaceAll("\\s+", " ");
            System.out.println("  [" + label + "] SQL[" + i + "]: "
                    + sql.substring(0, Math.min(115, sql.length())));
        }
    }

    private UUID createPackage() throws Exception {
        String admin = adminToken();
        String suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        String created = mockMvc.perform(post("/api/v1/access-packages")
                        .header(AUTHORIZATION, bearer(admin)).contentType(JSON)
                        .content("{\"code\":\"G059-PKG-" + suffix + "\",\"name\":\"G059 Package\"}"))
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
}
