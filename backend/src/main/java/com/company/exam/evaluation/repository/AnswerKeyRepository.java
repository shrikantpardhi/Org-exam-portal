package com.company.exam.evaluation.repository;

import com.company.exam.evaluation.entity.AnswerKey;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Persistence for {@link AnswerKey} ({@code specs/15-P6-Evaluation-V1.5.md §5.1, §128}). */
public interface AnswerKeyRepository extends JpaRepository<AnswerKey, UUID> {

    List<AnswerKey> findByAssessmentVersionIdOrderByVersionDesc(UUID assessmentVersionId);

    /**
     * Locks every answer key of an assessment version for the duration of the transaction. Rank
     * recomputation takes this lock first so concurrent publications of the same assessment version
     * serialize and never produce torn rankings ({@code specs/15-P6 §38}).
     */
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select k from AnswerKey k where k.assessmentVersionId = :assessmentVersionId")
    List<AnswerKey> lockByAssessmentVersionId(@Param("assessmentVersionId") UUID assessmentVersionId);

    /** Every key id bound to a set of assessment versions, used to bound the staff result list. */
    @Query("select k.id from AnswerKey k where k.assessmentVersionId in :assessmentVersionIds")
    List<UUID> findIdsByAssessmentVersionIdIn(@Param("assessmentVersionIds") Collection<UUID> assessmentVersionIds);

    Optional<AnswerKey> findByAssessmentVersionIdAndVersion(UUID assessmentVersionId, Integer version);

    /** Next version for an assessment version; used to create a corrected key ({@code §52}). */
    @Query("select coalesce(max(k.version), 0) from AnswerKey k where k.assessmentVersionId = :assessmentVersionId")
    int findMaxVersion(@Param("assessmentVersionId") UUID assessmentVersionId);
}
