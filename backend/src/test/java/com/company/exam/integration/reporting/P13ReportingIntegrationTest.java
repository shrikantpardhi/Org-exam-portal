package com.company.exam.integration.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.exam.common.job.BackgroundJobRepository;
import com.company.exam.identity.entity.User;
import com.company.exam.integration.evaluation.AbstractEvaluationIntegrationTest;
import com.company.exam.reporting.worker.ReportWorker;
import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * P13 reporting end-to-end ({@code specs/22-P13-Reporting-V1.5.md §14-§31, §74, §76, §106}).
 *
 * <p>Verifies the three acceptance points directly: report reconciliation (a report reflects the
 * authoritative source without mutating it), the asynchronous job lifecycle, and protected download.
 */
class P13ReportingIntegrationTest extends AbstractEvaluationIntegrationTest {

    @Autowired
    private ReportWorker reportWorker;
    @Autowired
    private BackgroundJobRepository jobRepository;

    // ------------------------------------------------------------------ catalog and authorization

    @Test
    void catalogAdvertisesOnlyPermittedReportTypes() throws Exception {
        String admin = adminToken();
        String adminCatalog = mockMvc.perform(get("/api/v1/reports/catalog").header(AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        // The catalog lists the whole registered set, and every entry now has a provider, so every
        // advertised type is enabled and therefore actually generatable (§11, §46).
        assertThat(adminCatalog).contains("OPERATIONS_SYSTEM_HEALTH").contains("EXECUTIVE_KPI_SUMMARY")
                .contains("\"enabled\":true")
                .doesNotContain("\"enabled\":false");

        // A student holds no report permission (P1 froze an empty student permission set), so no report
        // type is advertised to them at all (§74, §77).
        User student = newStudent("p13-catalog");
        String studentCatalog = mockMvc.perform(get("/api/v1/reports/catalog")
                        .header(AUTHORIZATION, bearer(tokenFor(student))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(studentCatalog).doesNotContain("OPERATIONS").doesNotContain("EXECUTIVE")
                .doesNotContain("STUDENT_PROFILE");
    }

    @Test
    void requestingAnUnpermittedReportIsRefused() throws Exception {
        User student = newStudent("p13-denied");
        mockMvc.perform(post("/api/v1/reports").header(AUTHORIZATION, bearer(tokenFor(student))).contentType(JSON)
                        .content("{\"reportType\":\"EXECUTIVE_KPI_SUMMARY\",\"filters\":{},\"format\":\"CSV\"}"))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------ request validation

    @Test
    void requestValidationRejectsBadInputBeforeExecution() throws Exception {
        String admin = adminToken();
        // Unknown report type.
        mockMvc.perform(post("/api/v1/reports").header(AUTHORIZATION, bearer(admin)).contentType(JSON)
                        .content("{\"reportType\":\"NOPE\",\"filters\":{},\"format\":\"CSV\"}"))
                .andExpect(status().isBadRequest());
        // A filter that is not part of the report's schema.
        mockMvc.perform(post("/api/v1/reports").header(AUTHORIZATION, bearer(admin)).contentType(JSON)
                        .content("{\"reportType\":\"OPERATIONS_AUDIT_ACTIVITY\",\"filters\":{\"sql\":\"select 1\"},"
                                + "\"format\":\"CSV\"}"))
                .andExpect(status().isBadRequest());
        // An unordered date range.
        mockMvc.perform(post("/api/v1/reports").header(AUTHORIZATION, bearer(admin)).contentType(JSON)
                        .content("{\"reportType\":\"OPERATIONS_AUDIT_ACTIVITY\",\"filters\":{\"from\":"
                                + "\"2026-10-31T00:00:00Z\",\"to\":\"2026-10-01T00:00:00Z\"},\"format\":\"CSV\"}"))
                .andExpect(status().isBadRequest());
        // An interval beyond the configured maximum.
        mockMvc.perform(post("/api/v1/reports").header(AUTHORIZATION, bearer(admin)).contentType(JSON)
                        .content("{\"reportType\":\"OPERATIONS_AUDIT_ACTIVITY\",\"filters\":{\"from\":"
                                + "\"2000-01-01T00:00:00Z\",\"to\":\"2026-10-01T00:00:00Z\"},\"format\":\"CSV\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void everyCatalogEntryWithAProviderProducesAReport() throws Exception {
        String admin = adminToken();
        // GAP-141 acceptance: every type the catalog advertises is backed by a provider, so a request
        // for any of them succeeds. A synchronous type returns its projection; an ASYNC_PREFERRED type
        // is accepted for background generation. Either way the response is 200 and carries a payload
        // with a report type — never the "provider is not implemented" refusal and never a 5xx (§11, §46).
        for (com.company.exam.reporting.catalog.ReportType type
                : com.company.exam.reporting.catalog.ReportType.values()) {
            String body = "{\"reportType\":\"" + type.reportType() + "\",\"filters\":{},\"format\":\"CSV\"}";
            var result = mockMvc.perform(post("/api/v1/reports").header(AUTHORIZATION, bearer(admin))
                            .contentType(JSON).content(body))
                    .andExpect(status().isOk())
                    .andReturn();
            String response = result.getResponse().getContentAsString();
            assertThat(response)
                    .as("report type %s must generate, not be refused or fail", type.reportType())
                    .doesNotContain("not implemented");
            // A synchronous type returns its projection; an ASYNC_PREFERRED type returns the accepted
            // job. Both are a 200 above; the payload must be one of those two real shapes (§24, §28).
            boolean producedResult = response.contains("\"reportType\"");
            boolean acceptedForGeneration = response.contains("\"jobId\"");
            assertThat(producedResult || acceptedForGeneration)
                    .as("report type %s must return a projection or an accepted job", type.reportType())
                    .isTrue();
        }
    }

    // ------------------------------------------------------------------ synchronous report

    @Test
    void synchronousReportReturnsRowsAndDefinedMetrics() throws Exception {
        String admin = adminToken();
        mockMvc.perform(post("/api/v1/reports").header(AUTHORIZATION, bearer(admin)).contentType(JSON)
                        .content("{\"reportType\":\"OPERATIONS_SYSTEM_HEALTH\",\"filters\":{},\"format\":\"CSV\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reportType").value("OPERATIONS_SYSTEM_HEALTH"))
                .andExpect(jsonPath("$.data.format").value("CSV"))
                .andExpect(jsonPath("$.data.rowCount").isNumber())
                .andExpect(jsonPath("$.data.artifactName").value("operations-system-health.csv"));

        // Every executive KPI carries its explicit definition (§70).
        String kpis = mockMvc.perform(post("/api/v1/reports").header(AUTHORIZATION, bearer(admin)).contentType(JSON)
                        .content("{\"reportType\":\"EXECUTIVE_KPI_SUMMARY\",\"filters\":{},\"format\":\"CSV\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.metrics.length()").value(4))
                .andReturn().getResponse().getContentAsString();
        assertThat(kpis).contains("Count of users whose Identity lifecycle state is ACTIVE");
    }

    /** Filter values are data, never SQL: a hostile value returns an empty set and changes nothing (§106). */
    @Test
    void hostileFilterValuesAreTreatedAsData() throws Exception {
        String admin = adminToken();
        Long before = jdbcTemplate.queryForObject("select count(*) from audit_log", Long.class);
        mockMvc.perform(post("/api/v1/reports").header(AUTHORIZATION, bearer(admin)).contentType(JSON)
                        .content("{\"reportType\":\"OPERATIONS_USER_ACTIVITY\",\"filters\":{\"query\":"
                                + "\"'; drop table audit_log; --\"},\"format\":\"CSV\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rowCount").value(0));
        // The audit trail is intact and no mutation occurred (reports are read-only, §7.3).
        Long after = jdbcTemplate.queryForObject("select count(*) from audit_log", Long.class);
        assertThat(after).isGreaterThanOrEqualTo(before);
    }

    // ------------------------------------------------------------------ asynchronous lifecycle

    @Test
    void asynchronousReportLifecycleQueuesRunsAndDownloads() throws Exception {
        String admin = adminToken();
        // A large report is queued, never executed on the request thread (§7.5, §24).
        String accepted = mockMvc.perform(post("/api/v1/reports").header(AUTHORIZATION, bearer(admin))
                        .contentType(JSON)
                        .content("{\"reportType\":\"OPERATIONS_AUDIT_ACTIVITY\",\"filters\":{},\"format\":\"CSV\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("QUEUED"))
                .andReturn().getResponse().getContentAsString();
        String jobId = JsonPath.read(accepted, "$.data.jobId");

        assertThat(jdbcTemplate.queryForObject("select job_type from background_job where id = ?", String.class,
                UUID.fromString(jobId))).isEqualTo("REPORT_GENERATION");
        assertThat(jdbcTemplate.queryForObject("select status from background_job where id = ?", String.class,
                UUID.fromString(jobId))).isEqualTo("QUEUED");

        mockMvc.perform(get("/api/v1/reports/jobs/" + jobId).header(AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("QUEUED"))
                .andExpect(jsonPath("$.data.downloadUrl").doesNotExist());

        // The worker performs the generation.
        assertThat(reportWorker.processQueued()).isGreaterThanOrEqualTo(1);

        String completed = mockMvc.perform(get("/api/v1/reports/jobs/" + jobId).header(AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<String>read(completed, "$.data.downloadUrl")).contains(jobId);

        // The artifact is downloadable and is a real CSV.
        byte[] artifact = mockMvc.perform(get("/api/v1/reports/jobs/" + jobId + "/download")
                        .header(AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(artifact, java.nio.charset.StandardCharsets.UTF_8)).contains("Audit id");
    }

    @Test
    void reportHistoryIsServedFromBackgroundJob() throws Exception {
        String admin = adminToken();
        String accepted = mockMvc.perform(post("/api/v1/reports").header(AUTHORIZATION, bearer(admin))
                        .contentType(JSON)
                        .content("{\"reportType\":\"OPERATIONS_AUDIT_ACTIVITY\",\"filters\":{},\"format\":\"CSV\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String jobId = JsonPath.read(accepted, "$.data.jobId");
        reportWorker.processQueued();

        // History is the background_job rows — no reporting table exists (§29, §30).
        String history = mockMvc.perform(get("/api/v1/reports/history").header(AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(history).contains(jobId).contains("OPERATIONS_AUDIT_ACTIVITY");
    }

    // ------------------------------------------------------------------ protected download

    @Test
    void protectedDownloadRefusesAnotherUserAndTamperedTokens() throws Exception {
        String admin = adminToken();
        String accepted = mockMvc.perform(post("/api/v1/reports").header(AUTHORIZATION, bearer(admin))
                        .contentType(JSON)
                        .content("{\"reportType\":\"OPERATIONS_AUDIT_ACTIVITY\",\"filters\":{},\"format\":\"CSV\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String jobId = JsonPath.read(accepted, "$.data.jobId");
        reportWorker.processQueued();

        User student = newStudent("p13-idor");
        String studentToken = tokenFor(student);

        // A student cannot read another principal's report job (§27, §81).
        mockMvc.perform(get("/api/v1/reports/jobs/" + jobId).header(AUTHORIZATION, bearer(studentToken)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/reports/jobs/" + jobId + "/download")
                        .header(AUTHORIZATION, bearer(studentToken)))
                .andExpect(status().isForbidden());

        // A forged or tampered download token is not accepted either.
        mockMvc.perform(get("/api/v1/reports/jobs/" + jobId + "/download?token=forged.token.value")
                        .header(AUTHORIZATION, bearer(studentToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void downloadReportsAMissingArtifactForAnUnfinishedJob() throws Exception {
        String admin = adminToken();
        String accepted = mockMvc.perform(post("/api/v1/reports").header(AUTHORIZATION, bearer(admin))
                        .contentType(JSON)
                        .content("{\"reportType\":\"OPERATIONS_AUDIT_ACTIVITY\",\"filters\":{},\"format\":\"CSV\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String jobId = JsonPath.read(accepted, "$.data.jobId");

        // The job is queued but has no artifact yet: a controlled 404, never a path or a stack trace (§28).
        mockMvc.perform(get("/api/v1/reports/jobs/" + jobId + "/download").header(AUTHORIZATION, bearer(admin)))
                .andExpect(status().isNotFound());
    }

    @Test
    void aClaimedJobIsNotProcessedTwice() throws Exception {
        String admin = adminToken();
        String accepted = mockMvc.perform(post("/api/v1/reports").header(AUTHORIZATION, bearer(admin))
                        .contentType(JSON)
                        .content("{\"reportType\":\"OPERATIONS_AUDIT_ACTIVITY\",\"filters\":{},\"format\":\"CSV\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String jobId = JsonPath.read(accepted, "$.data.jobId");

        reportWorker.processQueued();
        String statusAfterFirst = jdbcTemplate.queryForObject("select status from background_job where id = ?",
                String.class, UUID.fromString(jobId));
        reportWorker.processQueued();
        String statusAfterSecond = jdbcTemplate.queryForObject("select status from background_job where id = ?",
                String.class, UUID.fromString(jobId));
        assertThat(statusAfterFirst).isEqualTo("COMPLETED");
        assertThat(statusAfterSecond).isEqualTo("COMPLETED");
        assertThat(jobRepository.findById(UUID.fromString(jobId)).orElseThrow().getAttemptCount()).isEqualTo(1);
    }
}
