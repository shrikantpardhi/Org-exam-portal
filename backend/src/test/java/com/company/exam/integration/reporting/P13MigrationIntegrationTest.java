package com.company.exam.integration.reporting;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.exam.support.AbstractIntegrationTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Proves P13 creates no reporting tables and seeds the report permissions
 * ({@code specs/22-P13-Reporting-V1.5.md §8, §25, §30, §75}).
 */
class P13MigrationIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void reportingMigrationIsAppliedSuccessfully() {
        Integer applied = jdbcTemplate.queryForObject(
                "select count(*) from flyway_schema_history where version = '24' and success = true",
                Integer.class);
        assertThat(applied).isEqualTo(1);
    }

    @Test
    void noReportingTableWasCreated() {
        // Report history lives in background_job and the catalog is application code; a report,
        // report_job, report_schedule, report_artifact or report_history table is not authorized (§8, §25).
        for (String forbidden : List.of("report", "report_job", "report_jobs", "report_history", "report_artifact",
                "report_artifacts", "report_schedule", "report_definition", "report_catalog", "export_job")) {
            Boolean exists = jdbcTemplate.queryForObject("select to_regclass(?) is not null", Boolean.class,
                    "public." + forbidden);
            assertThat(exists).as("table %s must not exist", forbidden).isFalse();
        }
    }

    @Test
    void reportHistoryIsCarriedByBackgroundJob() {
        // Report history reuses the general-purpose job table: it carries the job type, the owning
        // principal, the request fingerprint and the artifact reference — no new table is needed (§30).
        Integer requiredColumns = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.columns where table_name = 'background_job' "
                        + "and column_name in ('job_type', 'initiated_by', 'parameters', 'result_reference')",
                Integer.class);
        assertThat(requiredColumns).isEqualTo(4);

        // Job types are values, not a table: the report type is stored as a plain column value.
        Boolean jobTypeTable = jdbcTemplate.queryForObject("select to_regclass(?) is not null", Boolean.class,
                "public.job_type");
        assertThat(jobTypeTable).isFalse();
    }

    @Test
    void reportPermissionsAreSeededOnTheExpectedRoles() {
        Integer admin = jdbcTemplate.queryForObject(
                "select count(*) from role_permission rp join role r on r.id = rp.role_id "
                        + "join permission p on p.id = rp.permission_id "
                        + "where r.code = 'ADMIN' and p.code in ('REPORT_OPERATIONS_READ', "
                        + "'REPORT_EXECUTIVE_READ', 'REPORT_COMMERCE_READ', 'REPORT_GENERATE')",
                Integer.class);
        assertThat(admin).isEqualTo(4);

        // FINANCE is scoped to commerce reporting only (§79).
        Integer financeExecutive = jdbcTemplate.queryForObject(
                "select count(*) from role_permission rp join role r on r.id = rp.role_id "
                        + "join permission p on p.id = rp.permission_id "
                        + "where r.code = 'FINANCE' and p.code in ('REPORT_EXECUTIVE_READ', "
                        + "'REPORT_OPERATIONS_READ')",
                Integer.class);
        assertThat(financeExecutive).isZero();

        // A student never receives operational, executive or commerce reporting (§76).
        Integer student = jdbcTemplate.queryForObject(
                "select count(*) from role_permission rp join role r on r.id = rp.role_id "
                        + "join permission p on p.id = rp.permission_id "
                        + "where r.code = 'STUDENT' and p.code in ('REPORT_OPERATIONS_READ', "
                        + "'REPORT_EXECUTIVE_READ', 'REPORT_COMMERCE_READ', 'REPORT_ASSESSMENT_READ', "
                        + "'REPORT_CONTENT_READ')",
                Integer.class);
        assertThat(student).isZero();
    }

    @Test
    void aStudentHoldsExactlyTheSelfServiceReportPermissionAndNothingWider() {
        // GAP-142 / ADR-0030: student reporting is permission-based, not role-based, because P1 §9
        // says not to encode business authorization as a role branch. Exactly one report permission
        // is granted, so every other reporting capability stays out of reach.
        Integer granted = jdbcTemplate.queryForObject(
                "select count(*) from role_permission rp join role r on r.id = rp.role_id "
                        + "join permission p on p.id = rp.permission_id "
                        + "where r.code = 'STUDENT' and p.code like 'REPORT_%'",
                Integer.class);
        assertThat(granted).isEqualTo(1);

        Integer selfService = jdbcTemplate.queryForObject(
                "select count(*) from role_permission rp join role r on r.id = rp.role_id "
                        + "join permission p on p.id = rp.permission_id "
                        + "where r.code = 'STUDENT' and p.code = 'REPORT_STUDENT_READ'",
                Integer.class);
        assertThat(selfService).isEqualTo(1);

        // The wider reporting capabilities remain ungranted, including history and generation.
        Integer wider = jdbcTemplate.queryForObject(
                "select count(*) from role_permission rp join role r on r.id = rp.role_id "
                        + "join permission p on p.id = rp.permission_id "
                        + "where r.code = 'STUDENT' and p.code in ('REPORT_ASSESSMENT_READ', "
                        + "'REPORT_CONTENT_READ', 'REPORT_COMMERCE_READ', 'REPORT_OPERATIONS_READ', "
                        + "'REPORT_EXECUTIVE_READ', 'REPORT_HISTORY_READ', 'REPORT_GENERATE')",
                Integer.class);
        assertThat(wider).isZero();
    }
}
