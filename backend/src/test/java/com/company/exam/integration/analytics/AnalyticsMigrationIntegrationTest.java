package com.company.exam.integration.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.exam.support.AbstractIntegrationTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Proves the P8-02 Analytics schema migration and seed
 * ({@code specs/17-P8-Analytics-V1.5.md §5, §6, §150}).
 */
class AnalyticsMigrationIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void allAnalyticsTablesExist() {
        for (String table : new String[] {"student_analytics", "assessment_analytics", "question_analytics",
                "topic_analytics", "organization_analytics"}) {
            Boolean exists = jdbcTemplate.queryForObject("select to_regclass(?) is not null", Boolean.class,
                    "public." + table);
            assertThat(exists).as("table %s should exist", table).isTrue();
        }
    }

    @Test
    void analyticsMigrationsAreAppliedSuccessfully() {
        Integer applied = jdbcTemplate.queryForObject(
                "select count(*) from flyway_schema_history where version in ('16','17') and success = true",
                Integer.class);
        assertThat(applied).isEqualTo(2);
    }

    @Test
    void oneCurrentAggregateRowPerEntityIsEnforced() {
        for (String constraint : List.of("uk_student_analytics_user_id", "uk_assessment_analytics_assessment_id",
                "uk_question_analytics_question_id", "uk_topic_analytics_topic_id",
                "uk_organization_analytics_organization_id", "fk_student_analytics_user",
                "fk_assessment_analytics_assessment", "fk_question_analytics_question", "fk_topic_analytics_topic",
                "fk_organization_analytics_organization")) {
            Integer found = jdbcTemplate.queryForObject("select count(*) from pg_constraint where conname = ?",
                    Integer.class, constraint);
            assertThat(found).as("constraint %s", constraint).isEqualTo(1);
        }
        Integer index = jdbcTemplate.queryForObject(
                "select count(*) from pg_indexes where schemaname = 'public' and indexname = ?", Integer.class,
                "idx_student_analytics_user_id");
        assertThat(index).isEqualTo(1);
    }

    @Test
    void noTimeSeriesOrFactTablesWereIntroduced() {
        for (String table : new String[] {"analytics_event", "fact_analytics", "analytics_trend",
                "analytics_daily"}) {
            Boolean exists = jdbcTemplate.queryForObject("select to_regclass(?) is not null", Boolean.class,
                    "public." + table);
            assertThat(exists).as("table %s must not exist", table).isFalse();
        }
    }

    @Test
    void analyticsPermissionsAreSeededForAdminAndStaff() {
        Integer permissions = jdbcTemplate.queryForObject(
                "select count(*) from permission where code in ('ANALYTICS_VIEW','ANALYTICS_REFRESH',"
                        + "'ANALYTICS_REBUILD') and status = 'ACTIVE'",
                Integer.class);
        assertThat(permissions).isEqualTo(3);
        Integer staffView = jdbcTemplate.queryForObject(
                "select count(*) from role_permission rp join role r on rp.role_id = r.id "
                        + "join permission p on rp.permission_id = p.id where r.code='STAFF' and p.code='ANALYTICS_VIEW'",
                Integer.class);
        assertThat(staffView).isEqualTo(1);
    }
}
