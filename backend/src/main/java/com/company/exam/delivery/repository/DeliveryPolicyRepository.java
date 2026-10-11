package com.company.exam.delivery.repository;

import com.company.exam.common.enums.Status;
import com.company.exam.delivery.entity.DeliveryPolicy;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence for {@link DeliveryPolicy} ({@code §95}). */
public interface DeliveryPolicyRepository extends JpaRepository<DeliveryPolicy, UUID> {

    Optional<DeliveryPolicy> findByCode(String code);

    Optional<DeliveryPolicy> findByIdAndStatus(UUID id, Status status);
}
