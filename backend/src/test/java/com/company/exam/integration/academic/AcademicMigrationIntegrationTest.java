package com.company.exam.integration.academic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.company.exam.support.AbstractIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Proves the P2-02 Academic migration against a real PostgreSQL database
 * ({@code specs/11-P2-Academic-V1.5.md §106, §126}): the seven frozen tables exist, the scoped
 * unique constraints and indexes are installed, and orphan rows are rejected by the foreign keys.
 */
class AcademicMigrationIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void allSevenAcademicTablesExist() {
        for (String table : new String[] {"exam", "subject", "chapter", "topic", "sub_topic", "language", "tag"}) {
            Boolean exists = jdbcTemplate.queryForObject(
                    "select to_regclass(?) is not null", Boolean.class, "public." + table);
            assertThat(exists).as("table %s should exist", table).isTrue();
        }
    }

    @Test
    void academicMigrationsAreAppliedSuccessfully() {
        Integer applied = jdbcTemplate.queryForObject(
                "select count(*) from flyway_schema_history where version in ('4','5') and success = true",
                Integer.class);
        assertThat(applied).isEqualTo(2);
    }

    @Test
    void uniqueConstraintsAndIndexesAreInstalled() {
        for (String index : new String[] {
                "uk_exam_code", "uk_subject_exam_id_code", "uk_chapter_subject_id_code", "uk_topic_chapter_id_code",
                "uk_sub_topic_topic_id_code", "uk_language_code", "uk_tag_code",
                "idx_subject_exam_id", "idx_chapter_subject_id", "idx_topic_chapter_id", "idx_sub_topic_topic_id"}) {
            Integer found = jdbcTemplate.queryForObject(
                    "select count(*) from pg_indexes where schemaname = 'public' and indexname = ?",
                    Integer.class, index);
            assertThat(found).as("index %s should exist", index).isEqualTo(1);
        }
    }

    @Test
    void academicPermissionsAreSeededAndGrantedToAdmin() {
        Integer permissions = jdbcTemplate.queryForObject(
                "select count(*) from permission where code in ('EXAM_CREATE','SUBJECT_CREATE','CHAPTER_CREATE',"
                        + "'TOPIC_CREATE','SUB_TOPIC_CREATE','LANGUAGE_CREATE','TAG_CREATE') and status = 'ACTIVE'",
                Integer.class);
        assertThat(permissions).isEqualTo(7);
        Integer granted = jdbcTemplate.queryForObject(
                "select count(*) from role_permission rp join role r on r.id = rp.role_id "
                        + "join permission p on p.id = rp.permission_id "
                        + "where r.code = 'ADMIN' and p.code = 'EXAM_CREATE' and rp.status = 'ACTIVE'",
                Integer.class);
        assertThat(granted).isEqualTo(1);
    }

    @Test
    void duplicateExamCodeIsRejected() {
        String code = "EX-" + UUID.randomUUID();
        jdbcTemplate.update("insert into exam (id, code, name, status) values (?, ?, 'Name', 'ACTIVE')",
                UUID.randomUUID(), code);

        assertThatThrownBy(() -> jdbcTemplate.update(
                "insert into exam (id, code, name, status) values (?, ?, 'Name', 'ACTIVE')",
                UUID.randomUUID(), code))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uk_exam_code");
    }

    @Test
    void subjectCodeIsUniquePerExamButReusableAcrossExams() {
        UUID examA = insertExam("EXAM-A-" + UUID.randomUUID());
        UUID examB = insertExam("EXAM-B-" + UUID.randomUUID());

        jdbcTemplate.update("insert into subject (id, exam_id, code, name, status) values (?, ?, 'MATH', 'Math', 'ACTIVE')",
                UUID.randomUUID(), examA);

        // Same code under a different exam is allowed.
        jdbcTemplate.update("insert into subject (id, exam_id, code, name, status) values (?, ?, 'MATH', 'Math', 'ACTIVE')",
                UUID.randomUUID(), examB);

        // Same code under the same exam is rejected.
        assertThatThrownBy(() -> jdbcTemplate.update(
                "insert into subject (id, exam_id, code, name, status) values (?, ?, 'MATH', 'Math', 'ACTIVE')",
                UUID.randomUUID(), examA))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uk_subject_exam_id_code");
    }

    @Test
    void orphanChildIsRejectedByTheForeignKey() {
        assertThatThrownBy(() -> jdbcTemplate.update(
                "insert into subject (id, exam_id, code, name, status) values (?, ?, 'X', 'X', 'ACTIVE')",
                UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_subject_exam");
    }

    private UUID insertExam(String code) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("insert into exam (id, code, name, status) values (?, ?, 'Name', 'ACTIVE')", id, code);
        return id;
    }
}
