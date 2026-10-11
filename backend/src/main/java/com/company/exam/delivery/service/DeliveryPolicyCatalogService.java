package com.company.exam.delivery.service;

import com.company.exam.delivery.api.DeliveryPolicyCatalog;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Default {@link DeliveryPolicyCatalog} ({@code specs/13-P4-Assessment-V1.5.md §83.1}).
 *
 * <p>Reads the Delivery-owned {@code delivery_policy} table for existence. The table is created in
 * migration V8 (see {@code docs/known-gaps.md} GAP-040) because the frozen
 * {@code assessment_version.delivery_policy_id} foreign key requires it.
 */
@Service
public class DeliveryPolicyCatalogService implements DeliveryPolicyCatalog {

    private final JdbcTemplate jdbcTemplate;

    public DeliveryPolicyCatalogService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean exists(UUID deliveryPolicyId) {
        if (deliveryPolicyId == null) {
            return false;
        }
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from delivery_policy where id = ? and status = 'ACTIVE'",
                Integer.class, deliveryPolicyId);
        return count != null && count > 0;
    }
}
