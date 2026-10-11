package com.company.exam.delivery.application;

import com.company.exam.delivery.engine.DeliveryPolicyAttributes;
import com.company.exam.delivery.engine.DeliveryPolicyParser;
import com.company.exam.delivery.repository.DeliveryPolicyRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolves the effective {@code delivery_policy} attributes for an assessment version
 * ({@code specs/14-P5-Delivery-V1.5.md §95}).
 *
 * <p>A missing or malformed policy resolves to the safe defaults rather than failing the exam.
 */
@Service
public class DeliveryPolicyService {

    private final DeliveryPolicyRepository policyRepository;
    private final DeliveryPolicyParser parser;

    public DeliveryPolicyService(DeliveryPolicyRepository policyRepository, DeliveryPolicyParser parser) {
        this.policyRepository = policyRepository;
        this.parser = parser;
    }

    @Transactional(readOnly = true)
    public DeliveryPolicyAttributes resolve(UUID deliveryPolicyId) {
        if (deliveryPolicyId == null) {
            return DeliveryPolicyAttributes.defaults();
        }
        return policyRepository.findById(deliveryPolicyId)
                .map(policy -> parser.parse(policy.getConfiguration()))
                .orElseGet(DeliveryPolicyAttributes::defaults);
    }
}
