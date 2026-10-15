package com.company.exam.commerce.entity;

import com.company.exam.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * The commercial offer for an access package ({@code specs/18-P9-Commerce-V1.5.md §7.3, §10}).
 *
 * <p>{@code price} is the only authoritative price input; the client never submits the payable
 * amount ({@code §11.1}).
 */
@Entity
@Table(name = "subscription_plan")
public class SubscriptionPlan extends BaseEntity {

    @Column(name = "access_package_id", nullable = false)
    private UUID accessPackageId;

    @Column(name = "code", nullable = false, length = 50)
    private String code;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "duration_days", nullable = false)
    private Integer durationDays;

    @Column(name = "price", nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    public UUID getAccessPackageId() {
        return accessPackageId;
    }

    public void setAccessPackageId(UUID accessPackageId) {
        this.accessPackageId = accessPackageId;
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

    public Integer getDurationDays() {
        return durationDays;
    }

    public void setDurationDays(Integer durationDays) {
        this.durationDays = durationDays;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }
}
