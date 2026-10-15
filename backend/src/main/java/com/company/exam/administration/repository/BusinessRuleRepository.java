package com.company.exam.administration.repository;

import com.company.exam.administration.entity.BusinessRule;
import com.company.exam.common.enums.Status;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence for {@link BusinessRule} ({@code specs/20-P11 §9}). */
public interface BusinessRuleRepository extends JpaRepository<BusinessRule, UUID> {

    Optional<BusinessRule> findByCode(String code);

    boolean existsByCode(String code);

    List<BusinessRule> findByStatus(Status status);
}
