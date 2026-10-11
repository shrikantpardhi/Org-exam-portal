package com.company.exam.assessment.repository;

import com.company.exam.assessment.entity.Blueprint;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence for {@link Blueprint} ({@code specs/13-P4-Assessment-V1.5.md §80}). */
public interface BlueprintRepository extends JpaRepository<Blueprint, UUID> {

    Optional<Blueprint> findByCode(String code);

    boolean existsByCode(String code);
}
