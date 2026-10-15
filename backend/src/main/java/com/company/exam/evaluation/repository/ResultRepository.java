package com.company.exam.evaluation.repository;

import com.company.exam.evaluation.entity.Result;
import com.company.exam.evaluation.entity.ResultStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Persistence for {@link Result} ({@code specs/15-P6-Evaluation-V1.5.md §5.4, §128}). */
public interface ResultRepository extends JpaRepository<Result, UUID>, JpaSpecificationExecutor<Result> {

    Optional<Result> findByEvaluationId(UUID evaluationId);

    /** Analytics: results for a bounded set of evaluations ({@code 17-P8 §96}). */
    List<Result> findByEvaluationIdIn(Collection<UUID> evaluationIds);

    /**
     * The rank/percentile population: the current (non-superseded) evaluation of every attempt bound
     * to the same AssessmentVersion whose published Result exists ({@code §35, §36}).
     *
     * <p>The join uses only Evaluation-owned tables; the AssessmentVersion is a raw UUID column on
     * {@code answer_key}. No cross-module repository is touched.
     */
    @Query("""
            select r from Result r, Evaluation e, AnswerKey k
             where r.evaluationId = e.id
               and e.answerKeyId = k.id
               and k.assessmentVersionId = :assessmentVersionId
               and e.supersededById is null
               and r.resultStatus = :published
            """)
    List<Result> findPublishedForVersion(@Param("assessmentVersionId") UUID assessmentVersionId,
            @Param("published") ResultStatus published);
}
