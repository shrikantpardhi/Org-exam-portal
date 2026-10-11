package com.company.exam.integration.assessment;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.exam.support.AbstractIntegrationTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Proves the P4-02 Assessment migration ({@code specs/13-P4-Assessment-V1.5.md §176}): the nine
 * frozen tables plus the delivery_policy prerequisite exist with their constraints and indexes.
 */
class AssessmentMigrationIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void allAssessmentTablesAndTheDeliveryPolicyPrerequisiteExist() {
        for (String table : new String[] {"delivery_policy", "blueprint", "blueprint_version", "assessment",
                "assessment_version", "assessment_question", "assessment_selection_rule", "assessment_schedule",
                "assessment_collection", "collection_assessment"}) {
            Boolean exists = jdbcTemplate.queryForObject(
                    "select to_regclass(?) is not null", Boolean.class, "public." + table);
            assertThat(exists).as("table %s should exist", table).isTrue();
        }
    }

    @Test
    void assessmentMigrationsAreAppliedSuccessfully() {
        Integer applied = jdbcTemplate.queryForObject(
                "select count(*) from flyway_schema_history where version in ('8','9','10') and success = true",
                Integer.class);
        assertThat(applied).isEqualTo(3);
    }

    @Test
    void keyConstraintsExist() {
        for (String constraint : List.of("uk_blueprint_code", "uk_blueprint_version_blueprint_id_version",
                "uk_assessment_code", "uk_assessment_version_assessment_id_version",
                "uk_assessment_question_version_question", "uk_assessment_selection_rule_version_section",
                "uk_assessment_collection_code", "uk_collection_assessment_collection_id_assessment_id",
                "ck_assessment_version_engine_type", "ck_assessment_selection_rule_target_count",
                "ck_assessment_schedule_mode", "fk_assessment_question_question_version",
                "fk_assessment_version_blueprint_version", "fk_assessment_version_delivery_policy")) {
            Integer found = jdbcTemplate.queryForObject(
                    "select count(*) from pg_constraint where conname = ?", Integer.class, constraint);
            assertThat(found).as("constraint %s", constraint).isEqualTo(1);
        }
    }

    @Test
    void indexesExist() {
        for (String index : List.of("idx_blueprint_version_blueprint_id", "idx_assessment_version_assessment_id",
                "idx_assessment_version_blueprint_version_id", "idx_assessment_version_engine_type",
                "idx_assessment_version_delivery_policy_id", "idx_assessment_question_assessment_version_id",
                "idx_assessment_question_question_version_id", "idx_assessment_selection_rule_version",
                "idx_assessment_schedule_assessment_version_id", "idx_assessment_schedule_start_time",
                "idx_assessment_schedule_status", "idx_collection_assessment_collection_id",
                "idx_collection_assessment_assessment_id")) {
            Integer found = jdbcTemplate.queryForObject(
                    "select count(*) from pg_indexes where schemaname = 'public' and indexname = ?",
                    Integer.class, index);
            assertThat(found).as("index %s", index).isEqualTo(1);
        }
    }

    @Test
    void assessmentPermissionsAndDefaultDeliveryPolicyAreSeeded() {
        Integer permissions = jdbcTemplate.queryForObject(
                "select count(*) from permission where code in ('BLUEPRINT_VIEW','ASSESSMENT_CREATE',"
                        + "'ASSESSMENT_VERSION_PUBLISH','ASSESSMENT_SCHEDULE_CLOSE') and status = 'ACTIVE'",
                Integer.class);
        assertThat(permissions).isEqualTo(4);
        Integer policies = jdbcTemplate.queryForObject(
                "select count(*) from delivery_policy where code = 'DEFAULT' and status = 'ACTIVE'", Integer.class);
        assertThat(policies).isEqualTo(1);
    }
}
