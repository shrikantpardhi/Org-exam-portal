package com.company.exam.commerce.repository;

import com.company.exam.commerce.entity.SubscriptionPlan;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Persistence for {@link SubscriptionPlan} ({@code specs/18-P9-Commerce-V1.5.md §7.3}). */
public interface SubscriptionPlanRepository extends JpaRepository<SubscriptionPlan, UUID>,
        JpaSpecificationExecutor<SubscriptionPlan> {

    Optional<SubscriptionPlan> findByCode(String code);

    boolean existsByCode(String code);

    List<SubscriptionPlan> findByAccessPackageId(UUID accessPackageId);
}
