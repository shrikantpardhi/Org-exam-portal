package com.company.exam.commerce.repository;

import com.company.exam.commerce.entity.PaymentStatus;
import com.company.exam.commerce.entity.Purchase;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

/** Persistence for {@link Purchase} ({@code specs/18-P9-Commerce-V1.5.md §7.4, §95}). */
public interface PurchaseRepository extends JpaRepository<Purchase, UUID>, JpaSpecificationExecutor<Purchase> {

    Optional<Purchase> findByIdAndUserId(UUID id, UUID userId);

    /** Idempotency: an existing open purchase for the same user/plan/coupon/amount ({@code §95}). */
    Optional<Purchase> findFirstByUserIdAndSubscriptionPlanIdAndCouponIdAndAmountAndPaymentStatusOrderByCreatedOnDesc(
            UUID userId, UUID subscriptionPlanId, UUID couponId, BigDecimal amount, PaymentStatus paymentStatus);

    List<Purchase> findByUserId(UUID userId);

    /** Locks the purchase for the callback / refund transaction ({@code §23, §97}). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Purchase p where p.id = :id")
    Optional<Purchase> findByIdForUpdate(@Param("id") UUID id);
}
