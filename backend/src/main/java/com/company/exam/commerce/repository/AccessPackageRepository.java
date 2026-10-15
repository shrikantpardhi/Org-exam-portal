package com.company.exam.commerce.repository;

import com.company.exam.commerce.entity.AccessPackage;
import com.company.exam.common.enums.Status;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Persistence for {@link AccessPackage} ({@code specs/18-P9-Commerce-V1.5.md §7.1}). */
public interface AccessPackageRepository extends JpaRepository<AccessPackage, UUID>,
        JpaSpecificationExecutor<AccessPackage> {

    Optional<AccessPackage> findByCode(String code);

    boolean existsByCode(String code);

    List<AccessPackage> findByStatus(Status status);
}
