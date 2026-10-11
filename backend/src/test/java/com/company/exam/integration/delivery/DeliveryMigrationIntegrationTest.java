package com.company.exam.integration.delivery;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.exam.support.AbstractIntegrationTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Proves the P5-02/P5-03 Delivery schema migration and seed
 * ({@code specs/14-P5-Delivery-V1.5.md §6-§12, §157, §159}).
 */
class DeliveryMigrationIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void allDeliveryRuntimeTablesAndTheJobPrerequisiteExist() {
        for (String table : new String[] {"delivery_policy", "student_attempt", "assessment_session",
                "attempt_question", "attempt_question_option", "student_response", "background_job"}) {
            Boolean exists = jdbcTemplate.queryForObject(
                    "select to_regclass(?) is not null", Boolean.class, "public." + table);
            assertThat(exists).as("table %s should exist", table).isTrue();
        }
    }

    @Test
    void deliveryMigrationsAreAppliedSuccessfully() {
        Integer applied = jdbcTemplate.queryForObject(
                "select count(*) from flyway_schema_history where version in ('11','12','13') and success = true",
                Integer.class);
        assertThat(applied).isEqualTo(3);
    }

    @Test
    void snapshotUniquenessAndStatusConstraintsExist() {
        for (String constraint : List.of("uk_attempt_question_attempt_question_version",
                "uk_attempt_question_attempt_display_order",
                "uk_attempt_question_option_question_option",
                "uk_attempt_question_option_display_order",
                "uk_student_response_attempt_question",
                "ck_student_attempt_snapshot_status", "ck_student_attempt_attempt_status",
                "ck_student_attempt_mode", "ck_assessment_session_status",
                "ck_background_job_status", "fk_assessment_session_attempt",
                "fk_attempt_question_attempt", "fk_attempt_question_option_attempt_question",
                "fk_student_response_attempt")) {
            Integer found = jdbcTemplate.queryForObject(
                    "select count(*) from pg_constraint where conname = ?", Integer.class, constraint);
            assertThat(found).as("constraint %s", constraint).isEqualTo(1);
        }
    }

    @Test
    void requiredIndexesExist() {
        for (String index : List.of("idx_student_attempt_user_id", "idx_student_attempt_snapshot_status",
                "idx_student_attempt_schedule_snapshot_status", "idx_assessment_session_user_id",
                "idx_attempt_question_attempt_order", "idx_attempt_question_option_attempt_question_id",
                "idx_student_response_attempt_id", "idx_background_job_job_type", "idx_background_job_status")) {
            Integer found = jdbcTemplate.queryForObject(
                    "select count(*) from pg_indexes where schemaname = 'public' and indexname = ?",
                    Integer.class, index);
            assertThat(found).as("index %s", index).isEqualTo(1);
        }
    }

    @Test
    void deliveryOperationalPermissionsAreSeeded() {
        Integer permissions = jdbcTemplate.queryForObject(
                "select count(*) from permission where code in ('DELIVERY_PREPARE_ATTEMPTS',"
                        + "'DELIVERY_ATTEMPT_VIEW_ANY','DELIVERY_OPERATE') and status = 'ACTIVE'",
                Integer.class);
        assertThat(permissions).isEqualTo(3);
    }
}
