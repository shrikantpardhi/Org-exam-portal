package com.company.exam.commerce.service;

import com.company.exam.commerce.dto.CommerceDtos;
import com.company.exam.commerce.entity.AccessPackage;
import com.company.exam.commerce.entity.Coupon;
import com.company.exam.commerce.entity.PaymentStatus;
import com.company.exam.commerce.entity.Purchase;
import com.company.exam.commerce.entity.SubscriptionPlan;
import com.company.exam.commerce.observability.CommerceMetrics;
import com.company.exam.commerce.pricing.PricingCalculator;
import com.company.exam.commerce.repository.PurchaseRepository;
import com.company.exam.common.api.ApiError;
import com.company.exam.common.audit.SecurityEventLogger;
import com.company.exam.common.enums.Status;
import com.company.exam.common.exception.InvalidStateException;
import com.company.exam.common.exception.NotFoundException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Purchase creation and queries ({@code specs/18-P9-Commerce-V1.5.md §11, §31, §67}).
 *
 * <p>The amount is always the server-calculated value: the client never determines the final price
 * ({@code §11.1}). A purchase is never the authorization record ({@code §3.1}). Creation is safe
 * under retries: an existing open purchase for the same user, plan, coupon and amount is returned
 * rather than duplicated ({@code §31, §95}).
 */
@Service
public class PurchaseService {

    private final PurchaseRepository purchaseRepository;
    private final CommerceCatalogService catalog;
    private final PricingCalculator pricingCalculator;
    private final CommerceMetrics metrics;
    private final SecurityEventLogger audit;

    public PurchaseService(PurchaseRepository purchaseRepository, CommerceCatalogService catalog,
            PricingCalculator pricingCalculator, CommerceMetrics metrics, SecurityEventLogger audit) {
        this.purchaseRepository = purchaseRepository;
        this.catalog = catalog;
        this.pricingCalculator = pricingCalculator;
        this.metrics = metrics;
        this.audit = audit;
    }

    @Transactional
    public CommerceDtos.PurchaseResponse create(UUID userId, CommerceDtos.CreatePurchaseRequest request) {
        SubscriptionPlan plan = catalog.requirePlan(request.subscriptionPlanId());
        if (plan.getStatus() != Status.ACTIVE) {
            metrics.purchaseCreateFailed();
            throw new InvalidStateException("The subscription plan is not active");
        }
        AccessPackage accessPackage = catalog.requirePackage(plan.getAccessPackageId());
        if (accessPackage.getStatus() != Status.ACTIVE) {
            metrics.purchaseCreateFailed();
            throw new InvalidStateException("The access package is not active");
        }

        Optional<Coupon> coupon = catalog.resolveValidCoupon(request.couponCode(), plan);
        PricingCalculator.PricingResult pricing = pricingCalculator.calculate(
                CommerceCatalogService.planSnapshot(plan), coupon.map(CommerceCatalogService::couponSnapshot));
        UUID couponId = coupon.map(Coupon::getId).orElse(null);

        Optional<Purchase> existing = purchaseRepository
                .findFirstByUserIdAndSubscriptionPlanIdAndCouponIdAndAmountAndPaymentStatusOrderByCreatedOnDesc(
                        userId, plan.getId(), couponId, pricing.finalAmount(), PaymentStatus.PENDING);
        if (existing.isPresent()) {
            return toResponse(existing.get(), plan, accessPackage);
        }

        Purchase purchase = new Purchase();
        purchase.setUserId(userId);
        purchase.setSubscriptionPlanId(plan.getId());
        purchase.setCouponId(couponId);
        purchase.setPurchaseDate(Instant.now());
        purchase.setAmount(pricing.finalAmount());
        purchase.setPaymentStatus(PaymentStatus.PENDING);
        Purchase saved = purchaseRepository.saveAndFlush(purchase);
        metrics.purchaseCreated();
        audit.audit(userId, "CREATE", "Purchase", saved.getId(), "Created purchase");
        return toResponse(saved, plan, accessPackage);
    }

    /** The caller's own purchase, or any purchase for a finance/admin caller ({@code §67}). */
    @Transactional(readOnly = true)
    public CommerceDtos.PurchaseResponse getVisible(UUID purchaseId, UUID userId, boolean staff) {
        Purchase purchase = purchaseRepository.findById(purchaseId)
                .orElseThrow(() -> NotFoundException.of("Purchase", purchaseId));
        if (!staff && !purchase.getUserId().equals(userId)) {
            throw NotFoundException.of("Purchase", purchaseId);
        }
        return describe(purchase);
    }

    @Transactional(readOnly = true)
    public List<CommerceDtos.PurchaseResponse> listForUser(UUID userId) {
        return purchaseRepository.findByUserId(userId).stream().map(this::describe).toList();
    }

    /** Loads an owned purchase or fails without leaking existence ({@code §67}). */
    @Transactional(readOnly = true)
    public Purchase requireOwned(UUID purchaseId, UUID userId) {
        return purchaseRepository.findByIdAndUserId(purchaseId, userId)
                .orElseThrow(() -> NotFoundException.of("Purchase", purchaseId));
    }

    private CommerceDtos.PurchaseResponse describe(Purchase purchase) {
        SubscriptionPlan plan = catalog.requirePlan(purchase.getSubscriptionPlanId());
        AccessPackage accessPackage = catalog.requirePackage(plan.getAccessPackageId());
        return toResponse(purchase, plan, accessPackage);
    }

    private CommerceDtos.PurchaseResponse toResponse(Purchase purchase, SubscriptionPlan plan,
            AccessPackage accessPackage) {
        return new CommerceDtos.PurchaseResponse(purchase.getId(), plan.getId(), accessPackage.getId(),
                plan.getName(), purchase.getCouponId(), purchase.getPurchaseDate(), purchase.getAmount(),
                purchase.getPaymentStatus() == null ? null : purchase.getPaymentStatus().name());
    }
}
