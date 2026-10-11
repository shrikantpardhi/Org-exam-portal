package com.company.exam.content.repository;

import com.company.exam.content.entity.PreviousExamHistory;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence for {@link PreviousExamHistory} ({@code specs/12-P3-Content-V1.5.md §44}). */
public interface PreviousExamHistoryRepository extends JpaRepository<PreviousExamHistory, UUID> {

    List<PreviousExamHistory> findByQuestionIdOrderByYearDesc(UUID questionId);

    Optional<PreviousExamHistory> findByIdAndQuestionId(UUID id, UUID questionId);
}
