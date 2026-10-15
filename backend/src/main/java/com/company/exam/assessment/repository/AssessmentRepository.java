package com.company.exam.assessment.repository;

import com.company.exam.assessment.entity.Assessment;
import com.company.exam.common.enums.Status;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Persistence for {@link Assessment} ({@code §80}). */
public interface AssessmentRepository extends JpaRepository<Assessment, UUID>, JpaSpecificationExecutor<Assessment> {

    Optional<Assessment> findByCode(String code);

    boolean existsByCode(String code);

    /** Qualifying assessments for analytics: only ACTIVE logical assessments are counted. */
    List<Assessment> findByStatus(Status status);
}
