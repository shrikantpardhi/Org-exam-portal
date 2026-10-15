package com.company.exam.administration.entity;

import com.company.exam.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * Reusable configurable master value ({@code specs/20-P11-Administration-V1.5.md §10}).
 *
 * <p>Consumers reference {@code category} and {@code code}; display names are presentation data and
 * backend calculations never depend on mutable names ({@code §89}). {@code display_order} may have
 * gaps ({@code §10.5}).
 */
@Entity
@Table(name = "lookup")
public class Lookup extends BaseEntity {

    @Column(name = "category", nullable = false, length = 100)
    private String category;

    @Column(name = "code", nullable = false, length = 100)
    private String code;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "display_order")
    private Integer displayOrder;

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

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

    public Integer getDisplayOrder() {
        return displayOrder;
    }

    public void setDisplayOrder(Integer displayOrder) {
        this.displayOrder = displayOrder;
    }
}
