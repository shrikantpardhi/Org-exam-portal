package com.company.exam.content.repository;

import com.company.exam.content.entity.QuestionTemplate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence for {@link QuestionTemplate} ({@code specs/12-P3-Content-V1.5.md §87}). */
public interface QuestionTemplateRepository extends JpaRepository<QuestionTemplate, UUID> {

    Optional<QuestionTemplate> findByCode(String code);

    boolean existsByCode(String code);
}
