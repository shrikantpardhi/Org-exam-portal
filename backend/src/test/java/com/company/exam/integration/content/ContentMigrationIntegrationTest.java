package com.company.exam.integration.content;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.exam.support.AbstractIntegrationTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Proves the P3-02 Content migration against a real PostgreSQL database
 * ({@code specs/12-P3-Content-V1.5.md §152}): the nine frozen tables, constraints, and indexes
 * exist, and the master data is seeded.
 */
class ContentMigrationIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void allNineContentTablesExist() {
        for (String table : new String[] {"question_template", "question", "question_version", "question_option",
                "asset", "explanation", "hint", "previous_exam_history", "question_tag"}) {
            Boolean exists = jdbcTemplate.queryForObject(
                    "select to_regclass(?) is not null", Boolean.class, "public." + table);
            assertThat(exists).as("table %s should exist", table).isTrue();
        }
    }

    @Test
    void contentMigrationsAreAppliedSuccessfully() {
        Integer applied = jdbcTemplate.queryForObject(
                "select count(*) from flyway_schema_history where version in ('6','7') and success = true",
                Integer.class);
        assertThat(applied).isEqualTo(2);
    }

    @Test
    void indexesAreInstalled() {
        for (String index : List.of("idx_question_template_id", "idx_question_exam_id", "idx_question_subject_id",
                "idx_question_chapter_id", "idx_question_topic_id", "idx_question_sub_topic_id",
                "idx_question_version_question_id", "idx_question_version_language_id",
                "idx_question_version_review_status", "idx_question_option_question_version_id",
                "idx_asset_question_version_id", "idx_explanation_question_version_id", "idx_hint_question_version_id",
                "idx_previous_exam_history_question_id", "idx_previous_exam_history_exam_id",
                "idx_question_tag_question_id", "idx_question_tag_tag_id")) {
            Integer found = jdbcTemplate.queryForObject(
                    "select count(*) from pg_indexes where schemaname = 'public' and indexname = ?",
                    Integer.class, index);
            assertThat(found).as("index %s", index).isEqualTo(1);
        }
    }

    @Test
    void keyConstraintsExist() {
        for (String constraint : List.of("uk_question_template_code", "uk_question_version_question_id_version",
                "uk_question_option_question_version_id_option_key",
                "uk_question_option_question_version_id_display_order", "uk_question_tag_question_id_tag_id",
                "ck_question_version_review_status", "fk_question_template", "fk_question_exam",
                "fk_question_version_question", "fk_question_version_language", "fk_question_option_question_version",
                "fk_asset_question_version", "fk_question_tag_tag")) {
            Integer found = jdbcTemplate.queryForObject(
                    "select count(*) from pg_constraint where conname = ?", Integer.class, constraint);
            assertThat(found).as("constraint %s", constraint).isEqualTo(1);
        }
    }

    @Test
    void contentPermissionsAndTemplatesAreSeeded() {
        Integer permissions = jdbcTemplate.queryForObject(
                "select count(*) from permission where code in ('QUESTION_VIEW','QUESTION_CREATE','QUESTION_UPDATE',"
                        + "'QUESTION_SUBMIT_REVIEW','QUESTION_REVIEW','QUESTION_PUBLISH','QUESTION_ARCHIVE') "
                        + "and status = 'ACTIVE'",
                Integer.class);
        assertThat(permissions).isEqualTo(7);

        Integer templates = jdbcTemplate.queryForObject(
                "select count(*) from question_template where status = 'ACTIVE'", Integer.class);
        assertThat(templates).isGreaterThanOrEqualTo(6);
    }
}
