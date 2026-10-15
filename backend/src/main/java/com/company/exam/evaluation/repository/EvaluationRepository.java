package com.company.exam.evaluation.repository;

import com.company.exam.evaluation.entity.Evaluation;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Persistence for {@link Evaluation} ({@code specs/15-P6-Evaluation-V1.5.md §5.3, §128}). */
public interface EvaluationRepository extends JpaRepository<Evaluation, UUID> {

    /** All versions for an attempt, newest first (history API, {@code §50}). */
    List<Evaluation> findByAttemptIdOrderByVersionDesc(UUID attemptId);

    /** The single current (non-superseded) evaluation, if any ({@code §44}). */
    Optional<Evaluation> findByAttemptIdAndSupersededByIdIsNull(UUID attemptId);

    /** Analytics: the current evaluation of a bounded attempt set ({@code 17-P8 §96}). */
    List<Evaluation> findByAttemptIdInAndSupersededByIdIsNull(Collection<UUID> attemptIds);

    /** Evaluation ids bound to a resolved answer-key set, used to bound the staff result list. */
    @Query("select e.id from Evaluation e where e.answerKeyId in :answerKeyIds")
    List<UUID> findIdsByAnswerKeyIdIn(@Param("answerKeyIds") Collection<UUID> answerKeyIds);

    /** Evaluation ids for a resolved attempt set, used to bound the staff result list. */
    @Query("select e.id from Evaluation e where e.attemptId in :attemptIds")
    List<UUID> findIdsByAttemptIdIn(@Param("attemptIds") Collection<UUID> attemptIds);
}
