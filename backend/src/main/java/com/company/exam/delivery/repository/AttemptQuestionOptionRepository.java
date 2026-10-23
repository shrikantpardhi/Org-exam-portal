package com.company.exam.delivery.repository;

import com.company.exam.delivery.entity.AttemptQuestionOption;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Persistence for the immutable {@link AttemptQuestionOption} ordering snapshot ({@code §85}). */
public interface AttemptQuestionOptionRepository extends JpaRepository<AttemptQuestionOption, UUID> {

    List<AttemptQuestionOption> findByAttemptQuestionIdOrderByDisplayOrderAsc(UUID attemptQuestionId);

    List<AttemptQuestionOption> findByAttemptQuestionIdInOrderByDisplayOrderAsc(Collection<UUID> attemptQuestionIds);

    long countByAttemptQuestionId(UUID attemptQuestionId);

    /**
     * Clears the option rows of every snapshot question for an attempt, ahead of a retry
     * ({@code §23}, GAP-157).
     *
     * <p>A failed preparation can leave a partially materialized snapshot behind. The frozen
     * {@code attempt_question} table carries {@code UNIQUE(attempt_id, question_version_id)}, so
     * re-preparing without clearing first would collide on the second attempt and fail forever. Options
     * are removed before their questions to respect the foreign key.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            delete from AttemptQuestionOption o
             where o.attemptQuestionId in
                   (select q.id from AttemptQuestion q where q.attemptId = :attemptId)
            """)
    int deleteByAttemptId(@Param("attemptId") UUID attemptId);
}
