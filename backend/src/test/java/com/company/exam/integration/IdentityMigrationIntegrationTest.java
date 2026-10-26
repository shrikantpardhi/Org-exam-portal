package com.company.exam.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.company.exam.support.AbstractIntegrationTest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Proves the P1-01 Identity migration against a real PostgreSQL database
 * ({@code specs/10-P1-Identity-V1.5.md §202}): the seven frozen tables exist, the
 * constraints and indexes are installed, and the mandated master data is seeded.
 */
class IdentityMigrationIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void allSevenIdentityTablesExist() {
        for (String table : new String[] {
                "organization", "user", "role", "permission", "user_role", "role_permission", "login_session"}) {
            Boolean exists = jdbcTemplate.queryForObject(
                    "select to_regclass(?) is not null", Boolean.class, "public.\"" + table + "\"");
            assertThat(exists).as("table %s should exist", table).isTrue();
        }
    }

    @Test
    void identityMigrationsAreAppliedSuccessfully() {
        Integer applied = jdbcTemplate.queryForObject(
                "select count(*) from flyway_schema_history where version in ('2','3') and success = true",
                Integer.class);
        assertThat(applied).isEqualTo(2);
    }

    @Test
    void requiredIndexesAndUniqueConstraintsAreInstalled() {
        for (String index : new String[] {
                // uk_role_code is an APPROVED AMENDMENT: §25.3 freezes UNIQUE(code) on role, but a
                // role can now carry an organization (ADR-0045 / V35), so the single global unique was
                // split into two partial uniques. The exhaustive pin is kept and the replacements are
                // named here, so an index that is in neither place still fails this test.
                "uk_role_code_platform", "uk_role_code_organization",
                "uk_organization_code", "uk_user_email", "uk_user_mobile", "uk_permission_code",
                "uk_user_role_user_id_role_id", "uk_role_permission_role_id_permission_id",
                "idx_user_organization_id", "idx_user_role_user_id", "idx_user_role_role_id",
                "idx_role_permission_role_id", "idx_role_permission_permission_id",
                "idx_login_session_user_id", "idx_login_session_login_time", "idx_login_session_session_status"}) {
            Integer found = jdbcTemplate.queryForObject(
                    "select count(*) from pg_indexes where schemaname = 'public' and indexname = ?",
                    Integer.class, index);
            assertThat(found).as("index %s should exist", index).isEqualTo(1);
        }
    }

    @Test
    void roleAndPermissionMasterDataAreSeeded() {
        for (String code : List.of("ADMIN", "REVIEWER", "STAFF", "STUDENT", "SUPPORT", "FINANCE")) {
            assertThat(count("select count(*) from role where code = '" + code + "' and status = 'ACTIVE'"))
                    .as("role %s", code).isEqualTo(1);
        }
        for (String code : List.of("USER_VIEW", "USER_CREATE", "USER_UPDATE", "USER_ARCHIVE", "ROLE_VIEW",
                "ROLE_CREATE", "ROLE_UPDATE", "ROLE_PERMISSION_VIEW", "ROLE_PERMISSION_ASSIGN",
                "PERMISSION_VIEW", "SESSION_VIEW", "ADMIN_ROLE_ASSIGN")) {
            assertThat(count("select count(*) from permission where code = '" + code + "' and status = 'ACTIVE'"))
                    .as("permission %s", code).isEqualTo(1);
        }
        for (String code : List.of("USER_VIEW", "USER_CREATE", "USER_UPDATE", "USER_ARCHIVE", "ROLE_VIEW",
                "ROLE_CREATE", "ROLE_UPDATE", "ROLE_PERMISSION_VIEW", "ROLE_PERMISSION_ASSIGN",
                "PERMISSION_VIEW", "SESSION_VIEW")) {
            assertThat(count("select count(*) from role_permission rp "
                    + "join role r on r.id = rp.role_id "
                    + "join permission p on p.id = rp.permission_id "
                    + "where r.code = 'ADMIN' and p.code = '" + code + "' and rp.status = 'ACTIVE'"))
                    .as("ADMIN holds %s", code).isEqualTo(1);
        }
    }

    @Test
    void systemActorIsSeededAndAdminRoleAssignIsNotGrantedToAdmin() {
        assertThat(count("select count(*) from \"user\" "
                + "where id = '00000000-0000-0000-0000-000000000001' "
                + "and email = 'system@platform.internal' and status = 'ACTIVE'")).isEqualTo(1);
        assertThat(count("select count(*) from role_permission rp "
                + "join role r on r.id = rp.role_id "
                + "join permission p on p.id = rp.permission_id "
                + "where r.code = 'ADMIN' and p.code = 'ADMIN_ROLE_ASSIGN'")).isZero();
    }

    @Test
    void statusCheckConstraintRejectsUnknownValues() {
        assertThatThrownBy(() -> jdbcTemplate.update(
                "insert into role (id, code, name, status) values (?, 'BROKEN', 'Broken', 'NOT_A_STATUS')",
                UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void duplicateEmailIsRejectedByTheDatabase() {
        String orgId = jdbcTemplate.queryForObject("select id::text from organization limit 1", String.class);
        String email = "duplicate-" + UUID.randomUUID() + "@example.com";
        jdbcTemplate.update("insert into \"user\" (id, organization_id, email, password, status) "
                + "values (?, ?::uuid, ?, '{noop}x', 'ACTIVE')",
                UUID.randomUUID(), orgId, email);

        assertThatThrownBy(() -> jdbcTemplate.update(
                "insert into \"user\" (id, organization_id, email, password, status) "
                        + "values (?, ?::uuid, ?, '{noop}x', 'ACTIVE')",
                UUID.randomUUID(), orgId, email))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private int count(String sql) {
        Integer value = jdbcTemplate.queryForObject(sql, Integer.class);
        return value == null ? 0 : value;
    }
}
