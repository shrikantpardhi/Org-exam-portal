package com.company.exam.assessment.repository;

import com.company.exam.assessment.entity.Assessment;
import com.company.exam.common.enums.Status;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Persistence for {@link Assessment} ({@code §80}). */
public interface AssessmentRepository extends JpaRepository<Assessment, UUID>, JpaSpecificationExecutor<Assessment> {

    Optional<Assessment> findByCode(String code);

    boolean existsByCode(String code);

    /** Qualifying assessments for analytics: only ACTIVE logical assessments are counted. */
    List<Assessment> findByStatus(Status status);

    /**
     * The assessments authored against an exam, resolved through the selection-rule provenance
     * ({@code specs/13-P4-Assessment-V1.5.md §45, §46, §64}; ADR-0027).
     *
     * <p>The frozen {@code assessment} table has no exam column ({@code 04-Schema §28.4}), so the link
     * is the {@code examId} in a version's selection-rule configuration — the scope the platform
     * itself acts on when it materializes the question pool. The join runs over indexed foreign keys
     * ({@code idx_assessment_selection_rule_version},
     * {@code idx_assessment_version_assessment_id}) and the JSONB extraction is applied only to the
     * rules of those versions.
     *
     * @param examId the exam id as stored in {@code rule_configuration->>'examId'}
     */
    @Query(value = """
            select distinct av.assessment_id
              from assessment_version av
              join assessment_selection_rule r on r.assessment_version_id = av.id
             where lower(r.rule_configuration->>'examId') = lower(:examId)
            """, nativeQuery = true)
    List<UUID> findAssessmentIdsBySelectionRuleExamId(@Param("examId") String examId);
}
