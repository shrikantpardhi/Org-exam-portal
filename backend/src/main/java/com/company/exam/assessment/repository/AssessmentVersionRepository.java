package com.company.exam.assessment.repository;

import com.company.exam.assessment.entity.AssessmentVersion;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Persistence for {@link AssessmentVersion} ({@code §5.4, §23, §50}). */
public interface AssessmentVersionRepository extends JpaRepository<AssessmentVersion, UUID> {

    List<AssessmentVersion> findByAssessmentIdOrderByVersionAsc(UUID assessmentId);

    Optional<AssessmentVersion> findByIdAndAssessmentId(UUID id, UUID assessmentId);

    boolean existsByAssessmentIdAndVersion(UUID assessmentId, Integer version);

    @Query("select coalesce(max(v.version), 0) from AssessmentVersion v where v.assessmentId = :assessmentId")
    int findMaxVersion(@Param("assessmentId") UUID assessmentId);
}
