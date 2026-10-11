package com.company.exam.assessment.repository;

import com.company.exam.assessment.entity.AssessmentSchedule;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Persistence for {@link AssessmentSchedule} ({@code §5.7, §67}). */
public interface AssessmentScheduleRepository extends JpaRepository<AssessmentSchedule, UUID>,
        JpaSpecificationExecutor<AssessmentSchedule> {

    List<AssessmentSchedule> findByAssessmentVersionId(UUID assessmentVersionId);
}
