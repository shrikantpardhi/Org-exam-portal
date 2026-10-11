package com.company.exam.academic.entity;

import com.company.exam.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * A reusable language master ({@code specs/11-P2-Academic-V1.5.md §9.6, §19}).
 *
 * <p>Not an enum: the catalog is master-data driven.
 */
@Entity
@Table(name = "language")
public class Language extends BaseEntity {

    @Column(name = "code", nullable = false, length = 20)
    private String code;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

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
}
