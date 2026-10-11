package com.company.exam.content.repository;

import com.company.exam.content.entity.Explanation;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence for {@link Explanation} ({@code specs/12-P3-Content-V1.5.md §39}). */
public interface ExplanationRepository extends JpaRepository<Explanation, UUID> {

    List<Explanation> findByQuestionVersionIdOrderByCreatedOnAsc(UUID questionVersionId);

    Optional<Explanation> findFirstByQuestionVersionIdOrderByCreatedOnAsc(UUID questionVersionId);
}
