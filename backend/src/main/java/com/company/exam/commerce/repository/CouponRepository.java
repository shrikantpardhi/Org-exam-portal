package com.company.exam.commerce.repository;

import com.company.exam.commerce.entity.Coupon;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Persistence for {@link Coupon} ({@code specs/18-P9-Commerce-V1.5.md §7.7}). */
public interface CouponRepository extends JpaRepository<Coupon, UUID>, JpaSpecificationExecutor<Coupon> {

    /** Codes are normalized to upper case before lookup ({@code §15.1}). */
    Optional<Coupon> findByCode(String code);

    boolean existsByCode(String code);
}
