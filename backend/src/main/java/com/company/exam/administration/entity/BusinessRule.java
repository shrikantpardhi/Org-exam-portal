package com.company.exam.administration.entity;

import com.company.exam.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Configurable business behaviour ({@code specs/20-P11-Administration-V1.5.md §9}).
 *
 * <p>Administration stores and governs rules; the owning module interprets them ({@code §9.2,
 * §88}). A rule is data, never a scripting engine ({@code §9.3}).
 */
@Entity
@Table(name = "business_rule")
public class BusinessRule extends BaseEntity {

    @Column(name = "code", nullable = false, length = 100)
    private String code;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "configuration", nullable = false, columnDefinition = "jsonb")
    private String configuration;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getConfiguration() {
        return configuration;
    }

    public void setConfiguration(String configuration) {
        this.configuration = configuration;
    }
}
