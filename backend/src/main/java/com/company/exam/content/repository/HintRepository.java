package com.company.exam.content.repository;

import com.company.exam.content.entity.Hint;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence for {@link Hint} ({@code specs/12-P3-Content-V1.5.md §40}). */
public interface HintRepository extends JpaRepository<Hint, UUID> {

    List<Hint> findByQuestionVersionIdOrderByCreatedOnAsc(UUID questionVersionId);

    Optional<Hint> findFirstByQuestionVersionIdOrderByCreatedOnAsc(UUID questionVersionId);
}
