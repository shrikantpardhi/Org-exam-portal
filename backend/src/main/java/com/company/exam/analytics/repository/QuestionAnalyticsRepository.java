package com.company.exam.analytics.repository;

import com.company.exam.analytics.entity.QuestionAnalytics;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Persistence for {@link QuestionAnalytics} ({@code specs/17-P8-Analytics-V1.5.md §6.3, §60, §96}). */
public interface QuestionAnalyticsRepository extends JpaRepository<QuestionAnalytics, UUID>,
        JpaSpecificationExecutor<QuestionAnalytics> {

    Optional<QuestionAnalytics> findByQuestionId(UUID questionId);

    List<QuestionAnalytics> findByQuestionIdIn(Collection<UUID> questionIds);
}
