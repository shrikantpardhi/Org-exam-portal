package com.company.exam.assessment.repository;

import com.company.exam.assessment.entity.AssessmentSchedule;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Persistence for {@link AssessmentSchedule} ({@code §5.7, §67}). */
public interface AssessmentScheduleRepository extends JpaRepository<AssessmentSchedule, UUID>,
        JpaSpecificationExecutor<AssessmentSchedule> {

    List<AssessmentSchedule> findByAssessmentVersionId(UUID assessmentVersionId);

    /**
     * The schedule and the assessment version it binds, read in one statement (GAP-059).
     *
     * <p>Start Exam needs both on every call and neither can change during the launch, so reading
     * them separately costs a round trip on the hottest write path
     * ({@code specs/14-P5-Delivery-V1.5.md §26}). {@code assessment_version_id} is a non-null
     * foreign key, so an existing schedule always yields a row.
     */
    @Query("""
            select s, v
              from AssessmentSchedule s
              join AssessmentVersion v on v.id = s.assessmentVersionId
             where s.id = :scheduleId
            """)
    List<Object[]> findScheduleWithVersion(@Param("scheduleId") UUID scheduleId);
}
