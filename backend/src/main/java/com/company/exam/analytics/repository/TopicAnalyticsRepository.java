package com.company.exam.analytics.repository;

import com.company.exam.analytics.entity.TopicAnalytics;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence for {@link TopicAnalytics} ({@code specs/17-P8-Analytics-V1.5.md §6.4, §96}). */
public interface TopicAnalyticsRepository extends JpaRepository<TopicAnalytics, UUID> {

    Optional<TopicAnalytics> findByTopicId(UUID topicId);

    List<TopicAnalytics> findByTopicIdIn(Collection<UUID> topicIds);

    List<TopicAnalytics> findAllByOrderByAccuracyDesc();
}
