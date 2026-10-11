package com.company.exam.delivery.entity;

import com.company.exam.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A configurable delivery policy ({@code specs/14-P5-Delivery-V1.5.md §12, §95}).
 *
 * <p>Owned by the Delivery domain. It is referenced by {@code assessment_version.delivery_policy_id}
 * and resolved into a normalized {@code DeliveryConfiguration} at runtime. The JSON is a declarative
 * experience and enforcement contract, never executable configuration ({@code §35}).
 */
@Entity
@Table(name = "delivery_policy")
public class DeliveryPolicy extends BaseEntity {

    @Column(name = "code", nullable = false, length = 50)
    private String code;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "configuration", columnDefinition = "jsonb")
    private String configuration;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getConfiguration() {
        return configuration;
    }

    public void setConfiguration(String configuration) {
        this.configuration = configuration;
    }
}
