package com.company.exam.content.repository;

import com.company.exam.content.entity.QuestionOption;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence for {@link QuestionOption} ({@code specs/12-P3-Content-V1.5.md §38}). */
public interface QuestionOptionRepository extends JpaRepository<QuestionOption, UUID> {

    List<QuestionOption> findByQuestionVersionIdOrderByDisplayOrderAsc(UUID questionVersionId);

    Optional<QuestionOption> findByIdAndQuestionVersionId(UUID id, UUID questionVersionId);

    boolean existsByQuestionVersionIdAndOptionKey(UUID questionVersionId, String optionKey);

    boolean existsByQuestionVersionIdAndDisplayOrder(UUID questionVersionId, Integer displayOrder);

    long countByQuestionVersionId(UUID questionVersionId);

    long countByQuestionVersionIdAndCorrectTrue(UUID questionVersionId);
}
