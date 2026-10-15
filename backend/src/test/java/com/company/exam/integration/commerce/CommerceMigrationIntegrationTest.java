package com.company.exam.integration.commerce;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.exam.support.AbstractIntegrationTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Proves the P9-02 … P9-13 Commerce schema migration and seed
 * ({@code specs/18-P9-Commerce-V1.5.md §7, §52, §139, §140}).
 */
class CommerceMigrationIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void allCommerceTablesExist() {
        for (String table : new String[] {"access_package", "access_package_collection", "subscription_plan",
                "purchase", "payment_record", "access_grant", "coupon"}) {
            Boolean exists = jdbcTemplate.queryForObject("select to_regclass(?) is not null", Boolean.class,
                    "public." + table);
            assertThat(exists).as("table %s should exist", table).isTrue();
        }
    }

    @Test
    void commerceMigrationsAreAppliedSuccessfully() {
        Integer applied = jdbcTemplate.queryForObject(
                "select count(*) from flyway_schema_history where version in ('18','19') and success = true",
                Integer.class);
        assertThat(applied).isEqualTo(2);
    }

    @Test
    void frozenUniqueConstraintsExist() {
        for (String constraint : List.of("uk_access_package_code",
                "uk_access_package_collection_package_collection", "uk_subscription_plan_code",
                "uk_payment_record_purchase_id", "uk_payment_record_payment_reference", "uk_coupon_code",
                "fk_access_package_collection_package", "fk_purchase_user", "fk_purchase_subscription_plan",
                "fk_purchase_coupon", "fk_payment_record_purchase", "fk_access_grant_user",
                "fk_access_grant_access_package", "ck_purchase_payment_status", "ck_payment_record_payment_status",
                "ck_access_grant_status", "ck_subscription_plan_duration", "ck_subscription_plan_price")) {
            Integer found = jdbcTemplate.queryForObject("select count(*) from pg_constraint where conname = ?",
                    Integer.class, constraint);
            assertThat(found).as("constraint %s", constraint).isEqualTo(1);
        }
    }

    @Test
    void requiredIndexesExist() {
        for (String index : List.of("idx_access_package_collection_access_package_id",
                "idx_access_package_collection_assessment_collection_id", "idx_subscription_plan_access_package_id",
                "idx_purchase_user_id", "idx_purchase_subscription_plan_id", "idx_purchase_coupon_id",
                "idx_purchase_purchase_date", "idx_purchase_payment_status", "idx_access_grant_user_id",
                "idx_access_grant_access_package_id", "idx_access_grant_start_date", "idx_access_grant_end_date",
                "idx_access_grant_status")) {
            Integer found = jdbcTemplate.queryForObject(
                    "select count(*) from pg_indexes where schemaname = 'public' and indexname = ?", Integer.class,
                    index);
            assertThat(found).as("index %s", index).isEqualTo(1);
        }
    }

    @Test
    void noUnapprovedCommerceTablesWereIntroduced() {
        for (String table : new String[] {"payment_event", "refund", "grant_source", "purchase_status",
                "coupon_usage"}) {
            Boolean exists = jdbcTemplate.queryForObject("select to_regclass(?) is not null", Boolean.class,
                    "public." + table);
            assertThat(exists).as("table %s must not exist", table).isFalse();
        }
    }

    @Test
    void commercePermissionsAreSeededForFinanceAndStaff() {
        Integer permissions = jdbcTemplate.queryForObject(
                "select count(*) from permission where code in ('ACCESS_PACKAGE_VIEW','ACCESS_PACKAGE_MANAGE',"
                        + "'SUBSCRIPTION_PLAN_VIEW','SUBSCRIPTION_PLAN_MANAGE','COUPON_VIEW','COUPON_MANAGE',"
                        + "'PURCHASE_VIEW','PURCHASE_REFUND','ACCESS_GRANT_VIEW','ACCESS_GRANT_MANAGE')"
                        + " and status = 'ACTIVE'",
                Integer.class);
        assertThat(permissions).isEqualTo(10);
        Integer financeRefund = jdbcTemplate.queryForObject(
                "select count(*) from role_permission rp join role r on rp.role_id = r.id "
                        + "join permission p on rp.permission_id = p.id "
                        + "where r.code='FINANCE' and p.code='PURCHASE_REFUND'",
                Integer.class);
        assertThat(financeRefund).isEqualTo(1);
    }
}
