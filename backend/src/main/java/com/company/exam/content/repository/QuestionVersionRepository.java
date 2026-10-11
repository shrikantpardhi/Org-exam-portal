package com.company.exam.content.repository;

import com.company.exam.content.entity.QuestionVersion;
import com.company.exam.content.entity.ReviewStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Persistence for {@link QuestionVersion} ({@code specs/12-P3-Content-V1.5.md §88}). */
public interface QuestionVersionRepository extends JpaRepository<QuestionVersion, UUID> {

    Page<QuestionVersion> findByQuestionId(UUID questionId, Pageable pageable);

    List<QuestionVersion> findByQuestionIdIn(Collection<UUID> questionIds);

    List<QuestionVersion> findByQuestionIdOrderByVersionAsc(UUID questionId);

    Optional<QuestionVersion> findByQuestionIdAndVersion(UUID questionId, Integer version);

    Optional<QuestionVersion> findByIdAndQuestionId(UUID id, UUID questionId);

    boolean existsByQuestionIdAndVersion(UUID questionId, Integer version);

    boolean existsByQuestionIdAndReviewStatus(UUID questionId, ReviewStatus reviewStatus);

    boolean existsByQuestionIdAndPublishedTrue(UUID questionId);

    @Query("select coalesce(max(v.version), 0) from QuestionVersion v where v.questionId = :questionId")
    int findMaxVersion(@Param("questionId") UUID questionId);

    /**
     * Eligible published question versions for assessment selection
     * ({@code specs/13-P4-Assessment-V1.5.md §35, §45}). Only published versions are eligible.
     */
    @Query("""
            select v from QuestionVersion v, Question q, QuestionTemplate t
            where v.questionId = q.id
              and q.templateId = t.id
              and v.published = true
              and (:templateCode is null or t.code = :templateCode)
              and (:examId is null or q.examId = :examId)
              and (:subjectId is null or q.subjectId = :subjectId)
              and (:chapterId is null or q.chapterId = :chapterId)
              and (:topicId is null or q.topicId = :topicId)
              and (:languageId is null or v.languageId = :languageId)
              and (:difficultiesEmpty = true or v.difficulty in :difficulties)
              and v.questionId not in :excludeQuestionIds
              and v.id not in :excludeQuestionVersionIds
            """)
    List<QuestionVersion> findEligibleVersions(@Param("templateCode") String templateCode,
            @Param("examId") UUID examId, @Param("subjectId") UUID subjectId, @Param("chapterId") UUID chapterId,
            @Param("topicId") UUID topicId, @Param("languageId") UUID languageId,
            @Param("difficultiesEmpty") boolean difficultiesEmpty,
            @Param("difficulties") Collection<com.company.exam.content.entity.Difficulty> difficulties,
            @Param("excludeQuestionIds") Collection<UUID> excludeQuestionIds,
            @Param("excludeQuestionVersionIds") Collection<UUID> excludeQuestionVersionIds);
}
