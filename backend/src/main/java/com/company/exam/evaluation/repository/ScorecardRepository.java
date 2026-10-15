package com.company.exam.evaluation.repository;

import com.company.exam.evaluation.entity.Scorecard;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence for {@link Scorecard} ({@code specs/15-P6-Evaluation-V1.5.md §5.5, §128}). */
public interface ScorecardRepository extends JpaRepository<Scorecard, UUID> {

    /** One scorecard per result via {@code UNIQUE(result_id)} ({@code §60}). */
    Optional<Scorecard> findByResultId(UUID resultId);
}
