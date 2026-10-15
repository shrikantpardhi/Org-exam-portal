package com.company.exam.administration.repository;

import com.company.exam.administration.entity.OrganizationSetting;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Persistence for {@link OrganizationSetting} ({@code specs/20-P11 §8}). */
public interface OrganizationSettingRepository extends JpaRepository<OrganizationSetting, UUID> {

    Optional<OrganizationSetting> findByOrganizationId(UUID organizationId);

    /**
     * Serializes concurrent updates for one organization ({@code specs/20-P11 §8.7}): the frozen
     * schema has no version column, so the strategy is server-side serialization per organization.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from OrganizationSetting s where s.organizationId = :organizationId")
    Optional<OrganizationSetting> lockByOrganizationId(@Param("organizationId") UUID organizationId);
}
