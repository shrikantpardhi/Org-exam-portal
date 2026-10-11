package com.company.exam.identity.repository;

import com.company.exam.common.enums.Status;
import com.company.exam.identity.entity.Organization;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence for {@link Organization} ({@code specs/10-P1-Identity-V1.5.md §7}). */
public interface OrganizationRepository extends JpaRepository<Organization, UUID> {

    Optional<Organization> findByCode(String code);

    List<Organization> findByStatus(Status status);
}
