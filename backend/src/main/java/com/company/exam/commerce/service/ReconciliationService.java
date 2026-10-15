package com.company.exam.commerce.service;

import com.company.exam.commerce.dto.CommerceDtos;
import com.company.exam.commerce.entity.PaymentRecord;
import com.company.exam.commerce.entity.PaymentStatus;
import com.company.exam.commerce.entity.Purchase;
import com.company.exam.commerce.gateway.PaymentGateway;
import com.company.exam.commerce.gateway.PaymentGatewayRegistry;
import com.company.exam.commerce.observability.CommerceMetrics;
import com.company.exam.commerce.repository.PaymentRecordRepository;
import com.company.exam.commerce.repository.PurchaseRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reconciliation of local payment state against the provider ({@code specs/18-P9-Commerce-V1.5.md §60, §61}).
 *
 * <p>It produces an application-level diagnostic report; the states are report concepts, not frozen
 * database values, and no reconciliation table is introduced ({@code §60}).
 */
@Service
public class ReconciliationService {

    private final PaymentRecordRepository paymentRepository;
    private final PurchaseRepository purchaseRepository;
    private final PaymentGatewayRegistry gatewayRegistry;
    private final CommerceMetrics metrics;

    public ReconciliationService(PaymentRecordRepository paymentRepository, PurchaseRepository purchaseRepository,
            PaymentGatewayRegistry gatewayRegistry, CommerceMetrics metrics) {
        this.paymentRepository = paymentRepository;
        this.purchaseRepository = purchaseRepository;
        this.gatewayRegistry = gatewayRegistry;
        this.metrics = metrics;
    }

    @Transactional(readOnly = true)
    public CommerceDtos.ReconciliationReport reconcile() {
        PaymentGateway gateway = gatewayRegistry.active();
        List<CommerceDtos.ReconciliationFinding> findings = new ArrayList<>();
        int checked = 0;
        for (PaymentRecord payment : paymentRepository.findAll()) {
            if (payment.getPaymentReference() == null || payment.getPaymentReference().isBlank()) {
                continue;
            }
            checked++;
            Optional<PaymentGateway.ProviderPaymentState> provider = gateway.query(payment.getPaymentReference());
            Purchase purchase = purchaseRepository.findById(payment.getPurchaseId()).orElse(null);
            String state = classify(payment, purchase, provider);
            if (!"MATCHED".equals(state)) {
                findings.add(new CommerceDtos.ReconciliationFinding(payment.getPaymentReference(),
                        payment.getPurchaseId(), payment.getPaymentStatus().name(),
                        provider.map(value -> value.outcome().name()).orElse(null), state));
                metrics.reconciliationMismatch();
            }
        }
        return new CommerceDtos.ReconciliationReport(checked, findings.size(), findings);
    }

    private String classify(PaymentRecord payment, Purchase purchase,
            Optional<PaymentGateway.ProviderPaymentState> provider) {
        PaymentStatus local = payment.getPaymentStatus();
        if (provider.isEmpty()) {
            return local == PaymentStatus.SUCCESS ? "LOCAL_SUCCESS_PROVIDER_UNKNOWN" : "REFERENCE_NOT_FOUND";
        }
        PaymentGateway.ProviderPaymentState state = provider.get();
        if (purchase != null && state.amount() != null && state.amount().compareTo(purchase.getAmount()) != 0) {
            return "AMOUNT_MISMATCH";
        }
        return switch (state.outcome()) {
            case SUCCESS -> local == PaymentStatus.SUCCESS ? "MATCHED"
                    : (local == PaymentStatus.FAILED ? "LOCAL_FAILED_PROVIDER_SUCCESS" : "LOCAL_PENDING_PROVIDER_SUCCESS");
            case FAILED -> local == PaymentStatus.FAILED ? "MATCHED" : "LOCAL_PENDING_PROVIDER_FAILED";
            case REFUNDED -> local == PaymentStatus.REFUNDED ? "MATCHED" : "LOCAL_SUCCESS_PROVIDER_REFUNDED";
            case PENDING -> local == PaymentStatus.PENDING ? "MATCHED" : "LOCAL_ADVANCED_PROVIDER_PENDING";
        };
    }
}
