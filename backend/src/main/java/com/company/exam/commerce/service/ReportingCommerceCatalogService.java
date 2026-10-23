package com.company.exam.commerce.service;

import com.company.exam.commerce.api.ReportingCommerceCatalog;
import com.company.exam.commerce.entity.AccessGrant;
import com.company.exam.commerce.entity.Coupon;
import com.company.exam.commerce.entity.PaymentRecord;
import com.company.exam.commerce.entity.PaymentStatus;
import com.company.exam.commerce.entity.Purchase;
import com.company.exam.commerce.entity.SubscriptionPlan;
import com.company.exam.commerce.repository.AccessGrantRepository;
import com.company.exam.commerce.repository.CouponRepository;
import com.company.exam.commerce.repository.PaymentRecordRepository;
import com.company.exam.commerce.repository.PurchaseRepository;
import com.company.exam.commerce.repository.SubscriptionPlanRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Default {@link ReportingCommerceCatalog}
 * ({@code specs/22-P13-Reporting-V1.5.md §10, §56-§62}).
 *
 * <p>Purchases are read per user or through the repository's full set and then filtered by the
 * inclusive date window and truncated to the caller's limit. Payments and access grants are derived
 * from their owning purchase/user set so no unbounded scan feeds a report ({@code §7.6}). Nothing is
 * mutated and no payment state is repaired ({@code §7.3}).
 */
@Service
public class ReportingCommerceCatalogService implements ReportingCommerceCatalog {

    private final PurchaseRepository purchaseRepository;
    private final PaymentRecordRepository paymentRepository;
    private final AccessGrantRepository accessGrantRepository;
    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final CouponRepository couponRepository;

    public ReportingCommerceCatalogService(PurchaseRepository purchaseRepository,
            PaymentRecordRepository paymentRepository, AccessGrantRepository accessGrantRepository,
            SubscriptionPlanRepository subscriptionPlanRepository, CouponRepository couponRepository) {
        this.purchaseRepository = purchaseRepository;
        this.paymentRepository = paymentRepository;
        this.accessGrantRepository = accessGrantRepository;
        this.subscriptionPlanRepository = subscriptionPlanRepository;
        this.couponRepository = couponRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<PurchaseRow> purchases(UUID userId, Instant from, Instant to, int limit) {
        List<Purchase> source = userId == null ? purchaseRepository.findAll() : purchaseRepository.findByUserId(userId);
        List<PurchaseRow> rows = new ArrayList<>();
        for (Purchase purchase : source) {
            if (!within(purchase.getPurchaseDate(), from, to)) {
                continue;
            }
            rows.add(new PurchaseRow(purchase.getId(), purchase.getUserId(), purchase.getSubscriptionPlanId(),
                    purchase.getCouponId(), purchase.getPurchaseDate(), purchase.getAmount(),
                    purchase.getPaymentStatus() == null ? null : purchase.getPaymentStatus().name()));
        }
        return truncate(rows, limit);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentRow> payments(Instant from, Instant to, int limit) {
        List<PaymentRow> rows = new ArrayList<>();
        for (PaymentRecord payment : paymentRepository.findAll()) {
            if (!within(payment.getPaymentDate(), from, to)) {
                continue;
            }
            rows.add(new PaymentRow(payment.getId(), payment.getPurchaseId(), payment.getPaymentReference(),
                    payment.getPaymentMethod(), payment.getPaymentDate(),
                    payment.getPaymentStatus() == null ? null : payment.getPaymentStatus().name()));
        }
        return truncate(rows, limit);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AccessGrantRow> accessGrants(UUID userId, Instant from, Instant to, int limit) {
        List<AccessGrant> source = userId == null ? accessGrantRepository.findAll()
                : accessGrantRepository.findByUserId(userId);
        List<AccessGrantRow> rows = new ArrayList<>();
        for (AccessGrant grant : source) {
            if (!within(grant.getStartDate(), from, to)) {
                continue;
            }
            rows.add(new AccessGrantRow(grant.getId(), grant.getUserId(), grant.getAccessPackageId(),
                    grant.getStartDate(), grant.getEndDate(),
                    grant.getStatus() == null ? null : grant.getStatus().name()));
        }
        return truncate(rows, limit);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SubscriptionPlanRow> subscriptionPlans(Instant from, Instant to, int limit) {
        List<SubscriptionPlanRow> rows = new ArrayList<>();
        for (SubscriptionPlan plan : subscriptionPlanRepository.findAll()) {
            if (!within(plan.getCreatedOn(), from, to)) {
                continue;
            }
            rows.add(new SubscriptionPlanRow(plan.getId(), plan.getAccessPackageId(), plan.getCode(),
                    plan.getName(), plan.getDurationDays(), plan.getPrice(),
                    plan.getStatus() == null ? null : plan.getStatus().name(), plan.getCreatedOn()));
        }
        return truncate(rows, limit);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CouponRow> coupons(String status, Instant from, Instant to, int limit) {
        String wanted = status == null || status.isBlank() ? null : status.trim().toUpperCase(Locale.ROOT);
        // Redemption counts come from the purchases that reference each coupon, so the report shows
        // usage rather than only the coupon catalogue (§61).
        Map<UUID, Long> redemptions = new HashMap<>();
        for (Purchase purchase : purchaseRepository.findAll()) {
            if (purchase.getCouponId() != null) {
                redemptions.merge(purchase.getCouponId(), 1L, Long::sum);
            }
        }
        List<CouponRow> rows = new ArrayList<>();
        for (Coupon coupon : couponRepository.findAll()) {
            if (!within(coupon.getCreatedOn(), from, to)) {
                continue;
            }
            String actual = coupon.getStatus() == null ? null : coupon.getStatus().name();
            if (wanted != null && !wanted.equals(actual)) {
                continue;
            }
            rows.add(new CouponRow(coupon.getId(), coupon.getCode(), coupon.getName(),
                    coupon.getDiscountValue(), coupon.getValidFrom(), coupon.getValidTo(), actual,
                    coupon.getCreatedOn(), redemptions.getOrDefault(coupon.getId(), 0L)));
        }
        return truncate(rows, limit);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RefundRow> refunds(Instant from, Instant to, int limit) {
        Map<UUID, PaymentRecord> paymentByPurchase = new HashMap<>();
        for (PaymentRecord payment : paymentRepository.findAll()) {
            paymentByPurchase.putIfAbsent(payment.getPurchaseId(), payment);
        }
        List<RefundRow> rows = new ArrayList<>();
        for (Purchase purchase : purchaseRepository.findAll()) {
            PaymentRecord payment = paymentByPurchase.get(purchase.getId());
            boolean refunded = purchase.getPaymentStatus() == PaymentStatus.REFUNDED
                    || (payment != null && payment.getPaymentStatus() == PaymentStatus.REFUNDED);
            if (!refunded) {
                continue;
            }
            // There is no dedicated refund timestamp: the schema records refund as a payment state,
            // not an event. updated_on is the only marker of when that state last changed, and it is
            // reported as such rather than presented as an authoritative refund date.
            Instant changedOn = purchase.getUpdatedOn() != null ? purchase.getUpdatedOn()
                    : (payment != null ? payment.getUpdatedOn() : null);
            if (!within(changedOn, from, to)) {
                continue;
            }
            rows.add(new RefundRow(purchase.getId(), purchase.getUserId(), purchase.getAmount(),
                    purchase.getPurchaseDate(), changedOn,
                    purchase.getPaymentStatus() == null ? null : purchase.getPaymentStatus().name(),
                    payment == null || payment.getPaymentStatus() == null ? null
                            : payment.getPaymentStatus().name()));
        }
        return truncate(rows, limit);
    }

    /** Inclusive window: a null bound is open on that side. */
    private boolean within(Instant value, Instant from, Instant to) {
        if (value == null) {
            return false;
        }
        if (from != null && value.isBefore(from)) {
            return false;
        }
        return to == null || !value.isAfter(to);
    }

    private <T> List<T> truncate(List<T> rows, int limit) {
        return rows.size() > limit ? List.copyOf(rows.subList(0, limit)) : List.copyOf(rows);
    }
}
