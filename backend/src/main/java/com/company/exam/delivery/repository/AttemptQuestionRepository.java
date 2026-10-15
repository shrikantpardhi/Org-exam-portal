package com.company.exam.delivery.repository;

import com.company.exam.delivery.entity.AttemptQuestion;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence for {@link AttemptQuestion}, the immutable snapshot ({@code §85}). */
public interface AttemptQuestionRepository extends JpaRepository<AttemptQuestion, UUID> {

    List<AttemptQuestion> findByAttemptIdOrderByDisplayOrderAsc(UUID attemptId);

    /** Analytics: snapshot questions for a bounded set of attempts ({@code 17-P8 §50, §104}). */
    List<AttemptQuestion> findByAttemptIdIn(java.util.Collection<UUID> attemptIds);

    /** Analytics: snapshot population per question version ({@code 17-P8 §33}). */
    @org.springframework.data.jpa.repository.Query("""
            select q.questionVersionId, count(q) from AttemptQuestion q
             where q.questionVersionId in :questionVersionIds
             group by q.questionVersionId
            """)
    List<Object[]> countByQuestionVersionIds(
            @org.springframework.data.repository.query.Param("questionVersionIds") java.util.Collection<UUID> questionVersionIds);

    Optional<AttemptQuestion> findByAttemptIdAndQuestionVersionId(UUID attemptId, UUID questionVersionId);

    boolean existsByAttemptIdAndQuestionVersionId(UUID attemptId, UUID questionVersionId);

    long countByAttemptId(UUID attemptId);

    /** Distinct section names in snapshot display order, for the normalized configuration. */
    @org.springframework.data.jpa.repository.Query("""
            select aq.sectionName from AttemptQuestion aq
             where aq.attemptId = :attemptId
               and aq.sectionName is not null
             group by aq.sectionName
             order by min(aq.displayOrder)
            """)
    List<String> findDistinctSectionNames(@org.springframework.data.repository.query.Param("attemptId") UUID attemptId);
}
