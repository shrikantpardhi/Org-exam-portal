package com.company.exam.integration.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.exam.support.AbstractIntegrationTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Proves the P6-02/P6-03 Evaluation schema migration and seed
 * ({@code specs/15-P6-Evaluation-V1.5.md §5, §30.1-§30.5, §171, §177}).
 */
class EvaluationMigrationIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void allEvaluationTablesExist() {
        for (String table : new String[] {"answer_key", "answer_key_detail", "evaluation", "result", "scorecard"}) {
            Boolean exists = jdbcTemplate.queryForObject(
                    "select to_regclass(?) is not null", Boolean.class, "public." + table);
            assertThat(exists).as("table %s should exist", table).isTrue();
        }
    }

    @Test
    void evaluationMigrationsAreAppliedSuccessfully() {
        Integer applied = jdbcTemplate.queryForObject(
                "select count(*) from flyway_schema_history where version in ('14','15') and success = true",
                Integer.class);
        assertThat(applied).isEqualTo(2);
    }

    @Test
    void uniquenessOutcomeAndStatusConstraintsExist() {
        for (String constraint : List.of("uk_answer_key_assessment_version_id_version",
                "uk_answer_key_detail_answer_key_id_question_version_id",
                "uk_evaluation_attempt_id_version", "uk_result_evaluation_id", "uk_scorecard_result_id",
                "ck_answer_key_detail_question_outcome", "ck_answer_key_detail_answer",
                "ck_evaluation_status", "ck_result_result_status",
                "ck_answer_key_status", "ck_answer_key_detail_status", "ck_scorecard_status",
                "fk_answer_key_assessment_version", "fk_answer_key_detail_answer_key",
                "fk_answer_key_detail_correct_option", "fk_evaluation_attempt", "fk_evaluation_answer_key",
                "fk_result_evaluation", "fk_scorecard_result")) {
            Integer found = jdbcTemplate.queryForObject(
                    "select count(*) from pg_constraint where conname = ?", Integer.class, constraint);
            assertThat(found).as("constraint %s", constraint).isEqualTo(1);
        }
    }

    @Test
    void atMostOneCurrentEvaluationPerAttemptIsEnforced() {
        Integer indexes = jdbcTemplate.queryForObject(
                "select count(*) from pg_indexes where schemaname = 'public' and indexname = ?",
                Integer.class, "uk_evaluation_current_attempt");
        assertThat(indexes).isEqualTo(1);
        String definition = jdbcTemplate.queryForObject(
                "select indexdef from pg_indexes where schemaname = 'public' and indexname = ?",
                String.class, "uk_evaluation_current_attempt");
        assertThat(definition).containsIgnoringCase("WHERE (superseded_by_id IS NULL)");
    }

    @Test
    void requiredIndexesExist() {
        for (String index : List.of("idx_answer_key_assessment_version_id", "idx_answer_key_detail_answer_key_id",
                "idx_answer_key_detail_question_version_id", "idx_evaluation_attempt_id",
                "idx_evaluation_answer_key_id", "idx_evaluation_current_lookup", "idx_result_rank",
                "idx_result_result_status", "idx_scorecard_result_id")) {
            Integer found = jdbcTemplate.queryForObject(
                    "select count(*) from pg_indexes where schemaname = 'public' and indexname = ?",
                    Integer.class, index);
            assertThat(found).as("index %s", index).isEqualTo(1);
        }
    }

    @Test
    void evaluationPermissionsAreSeededForAdminAndStaff() {
        Integer permissions = jdbcTemplate.queryForObject(
                "select count(*) from permission where code in ('ANSWER_KEY_VIEW','ANSWER_KEY_MANAGE',"
                        + "'ANSWER_KEY_PUBLISH','EVALUATION_VIEW','EVALUATION_VIEW_HISTORY','EVALUATION_RUN',"
                        + "'RESULT_VIEW','RESULT_PUBLISH','SCORECARD_VIEW','SCORECARD_GENERATE')"
                        + " and status = 'ACTIVE'",
                Integer.class);
        assertThat(permissions).isEqualTo(10);
        Integer adminGrants = jdbcTemplate.queryForObject(
                "select count(*) from role_permission rp join role r on rp.role_id = r.id "
                        + "join permission p on rp.permission_id = p.id "
                        + "where r.code = 'ADMIN' and p.code like 'ANSWER_KEY%'",
                Integer.class);
        assertThat(adminGrants).isEqualTo(3);
    }
}
