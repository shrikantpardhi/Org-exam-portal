package com.company.exam.assessment.repository;

import com.company.exam.assessment.entity.AssessmentQuestion;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence for {@link AssessmentQuestion} ({@code §5.5, §58}). */
public interface AssessmentQuestionRepository extends JpaRepository<AssessmentQuestion, UUID> {

    List<AssessmentQuestion> findByAssessmentVersionIdOrderByDisplayOrderAsc(UUID assessmentVersionId);

    Optional<AssessmentQuestion> findByIdAndAssessmentVersionId(UUID id, UUID assessmentVersionId);

    boolean existsByAssessmentVersionIdAndQuestionVersionId(UUID assessmentVersionId, UUID questionVersionId);

    long countByAssessmentVersionId(UUID assessmentVersionId);

    long countByAssessmentVersionIdAndSectionName(UUID assessmentVersionId, String sectionName);

    List<AssessmentQuestion> findByAssessmentVersionIdIn(List<UUID> assessmentVersionIds);
}
