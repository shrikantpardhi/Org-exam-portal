package com.company.exam.commerce.repository;

import com.company.exam.commerce.entity.PaymentRecord;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

/** Persistence for {@link PaymentRecord} ({@code specs/18-P9-Commerce-V1.5.md §7.5, §21}). */
public interface PaymentRecordRepository extends JpaRepository<PaymentRecord, UUID> {

    Optional<PaymentRecord> findByPurchaseId(UUID purchaseId);

    /** The callback idempotency anchor ({@code §21}). */
    Optional<PaymentRecord> findByPaymentReference(String paymentReference);

    /** Locks the payment record for the callback / refund transaction ({@code §23, §97}). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from PaymentRecord r where r.purchaseId = :purchaseId")
    Optional<PaymentRecord> findByPurchaseIdForUpdate(@Param("purchaseId") UUID purchaseId);
}
