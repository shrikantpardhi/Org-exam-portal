package com.company.exam.analytics.repository;

import com.company.exam.analytics.entity.AssessmentAnalytics;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence for {@link AssessmentAnalytics} ({@code specs/17-P8-Analytics-V1.5.md §6.2, §96}). */
public interface AssessmentAnalyticsRepository extends JpaRepository<AssessmentAnalytics, UUID> {

    Optional<AssessmentAnalytics> findByAssessmentId(UUID assessmentId);

    List<AssessmentAnalytics> findByAssessmentIdIn(Collection<UUID> assessmentIds);
}
