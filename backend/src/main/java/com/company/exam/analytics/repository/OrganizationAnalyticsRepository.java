package com.company.exam.analytics.repository;

import com.company.exam.analytics.entity.OrganizationAnalytics;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence for {@link OrganizationAnalytics} ({@code specs/17-P8-Analytics-V1.5.md §6.5, §96}). */
public interface OrganizationAnalyticsRepository extends JpaRepository<OrganizationAnalytics, UUID> {

    Optional<OrganizationAnalytics> findByOrganizationId(UUID organizationId);
}
