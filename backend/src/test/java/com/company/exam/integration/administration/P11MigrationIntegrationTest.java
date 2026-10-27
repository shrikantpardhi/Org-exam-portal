package com.company.exam.integration.administration;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.exam.support.AbstractIntegrationTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Proves the P11-02…P11-05 Administration schema migration, seeds, and boundaries
 * ({@code specs/20-P11-Administration-V1.5.md §7, §11, §80}).
 */
class P11MigrationIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void exactlyTheFourAdministrationTablesExist() {
        for (String table : new String[] {"organization_setting", "business_rule", "lookup", "audit_log"}) {
            Boolean exists = jdbcTemplate.queryForObject("select to_regclass(?) is not null", Boolean.class,
                    "public." + table);
            assertThat(exists).as("table %s should exist", table).isTrue();
        }
        // No support_ticket, incident, admin_action, import_history, bulk_operation, search_index,
        // health_history or configuration_version table is authorized (§7).
        for (String forbidden : new String[] {"support_ticket", "incident", "admin_action", "import_history",
                "bulk_operation", "search_index", "health_history", "configuration_version",
                "notification_delivery", "email_delivery"}) {
            Boolean exists = jdbcTemplate.queryForObject("select to_regclass(?) is not null", Boolean.class,
                    "public." + forbidden);
            assertThat(exists).as("table %s must not exist", forbidden).isFalse();
        }
    }

    @Test
    void administrationMigrationsAreAppliedSuccessfully() {
        Integer applied = jdbcTemplate.queryForObject(
                "select count(*) from flyway_schema_history where version in ('22','23') and success = true",
                Integer.class);
        assertThat(applied).isEqualTo(2);
    }

    @Test
    void requiredConstraintsAndIndexesExist() {
        for (String constraint : List.of("uk_organization_setting_organization", "uk_business_rule_code",
                "uk_lookup_category_code", "fk_audit_log_user", "fk_organization_setting_organization")) {
            Integer found = jdbcTemplate.queryForObject("select count(*) from pg_constraint where conname = ?",
                    Integer.class, constraint);
            assertThat(found).as("constraint %s", constraint).isEqualTo(1);
        }
        for (String index : List.of("idx_lookup_category", "idx_lookup_category_display_order",
                "idx_audit_log_user_id", "idx_audit_log_entity_name", "idx_audit_log_entity_id",
                "idx_audit_log_created_on")) {
            Integer found = jdbcTemplate.queryForObject(
                    "select count(*) from pg_indexes where schemaname = 'public' and indexname = ?", Integer.class,
                    index);
            assertThat(found).as("index %s", index).isEqualTo(1);
        }
    }

    @Test
    void auditLogHasExactlyTheFrozenColumns() {
        List<String> columns = jdbcTemplate.queryForList(
                "select column_name from information_schema.columns where table_name = 'audit_log'", String.class);
        // No request_id, old_values, new_values, ip_address or payload field (§11.3).
        //
        // organization_id is an APPROVED AMENDMENT, not part of §34.4: ADR-0046 / V36 add it so the audit
        // explorer can be scoped by organization (D-11). This assertion stays exhaustive on purpose — it
        // is the guard that catches any unapproved column — so an amendment must be added here and in
        // specs/04 §34.4 together. If a column appears that is in neither place, this fails.
        assertThat(columns).containsExactlyInAnyOrder("id", "user_id", "entity_name", "entity_id", "action",
                "remarks", "created_on", "organization_id");
    }

    @Test
    void administrationPermissionsAreSeededOntoAdminAndSupportOnly() {
        Integer adminPermissions = jdbcTemplate.queryForObject(
                "select count(*) from role_permission rp join role r on r.id = rp.role_id "
                        + "join permission p on p.id = rp.permission_id "
                        + "where r.code = 'ADMIN' and p.code in ('ORGANIZATION_SETTING_UPDATE', 'AUDIT_LOG_VIEW', "
                        + "'SYSTEM_HEALTH_VIEW', 'GLOBAL_SEARCH', 'IMPORT_EXECUTE', 'BULK_EXECUTE')",
                Integer.class);
        assertThat(adminPermissions).isEqualTo(6);

        // V40 (2026-10-26): organization rules are authored by the installation owner only. The
        // ADMIN role keeps VIEW — it can read what governs it — but must not be able to change a
        // rule that expresses platform policy.
        Integer adminRuleWrite = jdbcTemplate.queryForObject(
                "select count(*) from role_permission rp join role r on r.id = rp.role_id "
                        + "join permission p on p.id = rp.permission_id "
                        + "where r.code = 'ADMIN' and rp.status = 'ACTIVE' "
                        + "and p.code in ('BUSINESS_RULE_CREATE', 'BUSINESS_RULE_UPDATE')",
                Integer.class);
        assertThat(adminRuleWrite).as("ADMIN must not author business rules (V40, ADR-0052)").isZero();

        Integer adminRuleView = jdbcTemplate.queryForObject(
                "select count(*) from role_permission rp join role r on r.id = rp.role_id "
                        + "join permission p on p.id = rp.permission_id "
                        + "where r.code = 'ADMIN' and rp.status = 'ACTIVE' and p.code = 'BUSINESS_RULE_VIEW'",
                Integer.class);
        assertThat(adminRuleView).as("ADMIN keeps read access to the rules that govern it").isEqualTo(1);

        // P1 froze this invariant: ADMIN must not hold ADMIN_ROLE_ASSIGN (§18).
        Integer adminRoleAssign = jdbcTemplate.queryForObject(
                "select count(*) from role_permission rp join role r on r.id = rp.role_id "
                        + "join permission p on p.id = rp.permission_id "
                        + "where r.code = 'ADMIN' and p.code = 'ADMIN_ROLE_ASSIGN'",
                Integer.class);
        assertThat(adminRoleAssign).isZero();

        Integer supportVoice = jdbcTemplate.queryForObject(
                "select count(*) from role_permission rp join role r on r.id = rp.role_id "
                        + "join permission p on p.id = rp.permission_id "
                        + "where r.code = 'SUPPORT' and p.code = 'SUPPORT_USER_CONTACT'",
                Integer.class);
        assertThat(supportVoice).isEqualTo(1);

        // Support must not gain audit, settings, rules or scoring-adjacent capabilities (§35).
        Integer forbidden = jdbcTemplate.queryForObject(
                "select count(*) from role_permission rp join role r on r.id = rp.role_id "
                        + "join permission p on p.id = rp.permission_id "
                        + "where r.code = 'SUPPORT' and p.code in ('AUDIT_LOG_VIEW', 'ORGANIZATION_SETTING_UPDATE', "
                        + "'BUSINESS_RULE_UPDATE', 'LOOKUP_DELETE')",
                Integer.class);
        assertThat(forbidden).isZero();

        Integer studentPermissions = jdbcTemplate.queryForObject(
                "select count(*) from role_permission rp join role r on r.id = rp.role_id "
                        + "join permission p on p.id = rp.permission_id "
                        + "where r.code = 'STUDENT' and (p.code like 'SUPPORT_%' or p.code like 'GLOBAL_SEARCH%' "
                        + "or p.code = 'AUDIT_LOG_VIEW')",
                Integer.class);
        assertThat(studentPermissions).isZero();
    }

    /**
     * V39 (ADR-0052): the primary-administrator column exists, is nullable, and is unique per
     * organization by construction rather than by convention.
     */
    @Test
    void organizationHasANullablePrimaryAdministratorColumn() {
        Integer present = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.columns "
                        + "where table_name = 'organization' and column_name = 'primary_admin_user_id'",
                Integer.class);
        assertThat(present).as("organization.primary_admin_user_id should exist (V39)").isEqualTo(1);

        String nullable = jdbcTemplate.queryForObject(
                "select is_nullable from information_schema.columns "
                        + "where table_name = 'organization' and column_name = 'primary_admin_user_id'",
                String.class);
        assertThat(nullable).as("no backfill: existing organizations keep a NULL primary").isEqualTo("YES");

        // The FK is what makes 'at most one primary per organization' a schema property.
        Integer fk = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.table_constraints "
                        + "where table_name = 'organization' and constraint_name = 'fk_organization_primary_admin'",
                Integer.class);
        assertThat(fk).isEqualTo(1);
    }
}
