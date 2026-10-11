package com.company.exam.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.exam.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Proves the persistence foundation against a real PostgreSQL database
 * ({@code specs/09-P0-Foundation-V1.5.md §74}):
 * connection established, migration applied, the database can be queried, and transactions commit
 * and roll back as expected.
 */
class FlywayMigrationIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    void databaseConnectionIsEstablished() {
        assertThat(jdbcTemplate.queryForObject("select 1", Integer.class)).isEqualTo(1);
    }

    @Test
    void baseMigrationIsAppliedSuccessfully() {
        Integer applied = jdbcTemplate.queryForObject(
                "select count(*) from flyway_schema_history where version = ? and success = true",
                Integer.class, "1");

        assertThat(applied).isEqualTo(1);
    }

    @Test
    void baseSchemaExtensionIsInstalled() {
        Integer installed = jdbcTemplate.queryForObject(
                "select count(*) from pg_extension where extname = ?", Integer.class, "pg_trgm");

        assertThat(installed).isEqualTo(1);
    }

    @Test
    void transactionCommitsWhenItCompletes() {
        transactionTemplate.executeWithoutResult(
                status -> jdbcTemplate.execute("create table p0_tx_commit (id integer primary key)"));

        assertThat(tableExists("p0_tx_commit")).isTrue();

        jdbcTemplate.execute("drop table p0_tx_commit");
    }

    @Test
    void transactionRollsBackWhenMarkedRollbackOnly() {
        transactionTemplate.executeWithoutResult(status -> {
            jdbcTemplate.execute("create table p0_tx_rollback (id integer)");
            status.setRollbackOnly();
        });

        assertThat(tableExists("p0_tx_rollback")).isFalse();
    }

    private boolean tableExists(String table) {
        Boolean exists = jdbcTemplate.queryForObject(
                "select to_regclass(?) is not null", Boolean.class, "public." + table);
        return Boolean.TRUE.equals(exists);
    }
}
