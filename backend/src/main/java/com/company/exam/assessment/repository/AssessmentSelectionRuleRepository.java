package com.company.exam.assessment.repository;

import com.company.exam.assessment.entity.AssessmentSelectionRule;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence for {@link AssessmentSelectionRule} ({@code §5.6, §63}). */
public interface AssessmentSelectionRuleRepository extends JpaRepository<AssessmentSelectionRule, UUID> {

    List<AssessmentSelectionRule> findByAssessmentVersionIdOrderByDisplayOrderAsc(UUID assessmentVersionId);

    Optional<AssessmentSelectionRule> findByIdAndAssessmentVersionId(UUID id, UUID assessmentVersionId);

    boolean existsByAssessmentVersionIdAndSectionName(UUID assessmentVersionId, String sectionName);
}
