package com.company.exam.commerce.service;

import com.company.exam.commerce.dto.CommerceDtos;
import com.company.exam.commerce.entity.PaymentRecord;
import com.company.exam.commerce.entity.PaymentStatus;
import com.company.exam.commerce.entity.Purchase;
import com.company.exam.commerce.entity.SubscriptionPlan;
import com.company.exam.commerce.gateway.PaymentGateway;
import com.company.exam.commerce.gateway.PaymentGatewayRegistry;
import com.company.exam.commerce.observability.CommerceMetrics;
import com.company.exam.commerce.repository.PaymentRecordRepository;
import com.company.exam.commerce.repository.PurchaseRepository;
import com.company.exam.common.audit.SecurityEventLogger;
import com.company.exam.common.exception.InvalidStateException;
import com.company.exam.common.exception.NotFoundException;
import com.company.exam.common.exception.SystemException;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Full refunds and access revocation ({@code specs/18-P9-Commerce-V1.5.md §20, §26, §29.6, §97}).
 *
 * <p>Only successful payments can be refunded. The purchase and payment rows are locked, the
 * transition is validated, and the associated AccessGrant is moved to INACTIVE with its end date set
 * to the refund time ({@code §20}). A repeated refund is idempotent.
 */
@Service
public class RefundService {

    private final PurchaseRepository purchaseRepository;
    private final PaymentRecordRepository paymentRepository;
    private final CommerceCatalogService catalog;
    private final AccessGrantService accessGrantService;
    private final PaymentGatewayRegistry gatewayRegistry;
    private final CommerceMetrics metrics;
    private final SecurityEventLogger audit;
    private final org.springframework.context.ApplicationEventPublisher events;

    public RefundService(PurchaseRepository purchaseRepository, PaymentRecordRepository paymentRepository,
            CommerceCatalogService catalog, AccessGrantService accessGrantService,
            PaymentGatewayRegistry gatewayRegistry, CommerceMetrics metrics, SecurityEventLogger audit,
            org.springframework.context.ApplicationEventPublisher events) {
        this.purchaseRepository = purchaseRepository;
        this.paymentRepository = paymentRepository;
        this.catalog = catalog;
        this.accessGrantService = accessGrantService;
        this.gatewayRegistry = gatewayRegistry;
        this.metrics = metrics;
        this.audit = audit;
        this.events = events;
    }

    @Transactional
    public CommerceDtos.RefundResponse refund(UUID purchaseId, UUID actorId) {
        Purchase purchase = purchaseRepository.findByIdForUpdate(purchaseId)
                .orElseThrow(() -> NotFoundException.of("Purchase", purchaseId));
        PaymentRecord payment = paymentRepository.findByPurchaseIdForUpdate(purchaseId)
                .orElseThrow(() -> NotFoundException.of("Payment for purchase", purchaseId));

        if (purchase.getPaymentStatus() == PaymentStatus.REFUNDED) {
            return new CommerceDtos.RefundResponse(purchaseId, PaymentStatus.REFUNDED.name(), true);
        }
        if (purchase.getPaymentStatus() != PaymentStatus.SUCCESS) {
            throw new InvalidStateException("Only a successful payment can be refunded");
        }
        SubscriptionPlan plan = catalog.requirePlan(purchase.getSubscriptionPlanId());

        PaymentGateway.RefundResult result = gatewayRegistry.active().refund(new PaymentGateway.RefundCommand(
                purchaseId, payment.getPaymentReference(), purchase.getAmount(), purchaseId.toString()));
        if (!result.refunded()) {
            metrics.refundFailed();
            throw new SystemException("The payment provider did not confirm the refund", null);
        }

        Instant refundTime = Instant.now();
        payment.setPaymentStatus(PaymentStatus.REFUNDED);
        payment.setPaymentDate(refundTime);
        purchase.setPaymentStatus(PaymentStatus.REFUNDED);
        paymentRepository.saveAndFlush(payment);
        purchaseRepository.saveAndFlush(purchase);

        accessGrantService.revokeForPackage(purchase.getUserId(), plan.getAccessPackageId(), refundTime, actorId);
        metrics.refunded();
        audit.audit(actorId, "REFUND", "Purchase", purchase.getId(), "Refunded purchase and revoked access");
        events.publishEvent(new com.company.exam.common.events.BusinessEvents.PaymentRefundedEvent(purchase.getId(),
                purchase.getUserId()));
        return new CommerceDtos.RefundResponse(purchaseId, PaymentStatus.REFUNDED.name(), true);
    }
}
