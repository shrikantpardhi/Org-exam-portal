package com.company.exam.integration.communication;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.exam.support.AbstractIntegrationTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Proves the P10-02/P10-07/P10-15 Communication schema migration and seed
 * ({@code specs/19-P10-Communication-V1.5.md §7, §21, §139}).
 */
class CommunicationMigrationIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void allCommunicationTablesExist() {
        for (String table : new String[] {"notification", "email_template", "announcement"}) {
            Boolean exists = jdbcTemplate.queryForObject("select to_regclass(?) is not null", Boolean.class,
                    "public." + table);
            assertThat(exists).as("table %s should exist", table).isTrue();
        }
    }

    @Test
    void communicationMigrationsAreAppliedSuccessfully() {
        Integer applied = jdbcTemplate.queryForObject(
                "select count(*) from flyway_schema_history where version in ('20','21') and success = true",
                Integer.class);
        assertThat(applied).isEqualTo(2);
    }

    @Test
    void notificationStatusIsRestrictedToUnreadOrRead() {
        String definition = jdbcTemplate.queryForObject(
                "select pg_get_constraintdef(oid) from pg_constraint where conname = 'ck_notification_status'",
                String.class);
        assertThat(definition).contains("UNREAD").contains("READ");

        // The frozen minimal shape: no delivery_status/event_type/template_id/read_on/expires_on columns.
        List<String> columns = jdbcTemplate.queryForList(
                "select column_name from information_schema.columns where table_name = 'notification'",
                String.class);
        assertThat(columns).containsExactlyInAnyOrder("id", "user_id", "title", "message", "notification_status",
                "created_on");
    }

    @Test
    void requiredConstraintsAndIndexesExist() {
        for (String constraint : List.of("uk_email_template_code", "ck_email_template_status",
                "ck_announcement_status", "fk_notification_user", "fk_announcement_created_by")) {
            Integer found = jdbcTemplate.queryForObject("select count(*) from pg_constraint where conname = ?",
                    Integer.class, constraint);
            assertThat(found).as("constraint %s", constraint).isEqualTo(1);
        }
        for (String index : List.of("idx_notification_user_id", "idx_notification_notification_status",
                "idx_announcement_created_by", "idx_announcement_start_date", "idx_announcement_end_date",
                "idx_announcement_status")) {
            Integer found = jdbcTemplate.queryForObject(
                    "select count(*) from pg_indexes where schemaname = 'public' and indexname = ?", Integer.class,
                    index);
            assertThat(found).as("index %s", index).isEqualTo(1);
        }
    }

    @Test
    void templateCatalogAndPermissionsAreSeeded() {
        Integer templates = jdbcTemplate.queryForObject(
                "select count(*) from email_template where code in ('WELCOME','PASSWORD_RESET',"
                        + "'PURCHASE_CONFIRMATION','PAYMENT_FAILED','PAYMENT_REFUNDED','RESULT_PUBLISHED',"
                        + "'ASSESSMENT_SCHEDULED') and status = 'ACTIVE'",
                Integer.class);
        assertThat(templates).isEqualTo(7);
        Integer permissions = jdbcTemplate.queryForObject(
                "select count(*) from permission where code in ('COMMUNICATION_TEMPLATE_READ',"
                        + "'COMMUNICATION_TEMPLATE_WRITE','COMMUNICATION_TEMPLATE_DELETE','ANNOUNCEMENT_READ',"
                        + "'ANNOUNCEMENT_WRITE','ANNOUNCEMENT_PUBLISH','ANNOUNCEMENT_DELETE') and status = 'ACTIVE'",
                Integer.class);
        assertThat(permissions).isEqualTo(7);
    }
}
