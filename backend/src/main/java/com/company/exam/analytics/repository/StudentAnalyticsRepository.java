package com.company.exam.analytics.repository;

import com.company.exam.analytics.entity.StudentAnalytics;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence for {@link StudentAnalytics} ({@code specs/17-P8-Analytics-V1.5.md §6.1, §96}). */
public interface StudentAnalyticsRepository extends JpaRepository<StudentAnalytics, UUID> {

    Optional<StudentAnalytics> findByUserId(UUID userId);

    List<StudentAnalytics> findByUserIdIn(Collection<UUID> userIds);

    List<StudentAnalytics> findAllByOrderByAverageScoreDesc();
}
