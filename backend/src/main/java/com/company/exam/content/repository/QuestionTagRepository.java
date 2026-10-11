package com.company.exam.content.repository;

import com.company.exam.content.entity.QuestionTag;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence for {@link QuestionTag} ({@code specs/12-P3-Content-V1.5.md §49}). */
public interface QuestionTagRepository extends JpaRepository<QuestionTag, UUID> {

    List<QuestionTag> findByQuestionId(UUID questionId);

    Optional<QuestionTag> findByQuestionIdAndTagId(UUID questionId, UUID tagId);

    boolean existsByQuestionIdAndTagId(UUID questionId, UUID tagId);
}
