package com.company.exam.delivery.repository;

import com.company.exam.delivery.entity.StudentResponse;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence for {@link StudentResponse} ({@code §86}). */
public interface StudentResponseRepository extends JpaRepository<StudentResponse, UUID> {

    Optional<StudentResponse> findByAttemptIdAndQuestionVersionId(UUID attemptId, UUID questionVersionId);

    List<StudentResponse> findByAttemptId(UUID attemptId);

    /** Analytics: responses for a bounded set of attempts ({@code 17-P8 §51, §104}). */
    List<StudentResponse> findByAttemptIdIn(java.util.Collection<UUID> attemptIds);

    /** Analytics: answered-response population per question version ({@code 17-P8 §33}). */
    @org.springframework.data.jpa.repository.Query("""
            select r.questionVersionId, count(r) from StudentResponse r
             where r.questionVersionId in :questionVersionIds
               and r.response is not null
               and r.response <> ''
             group by r.questionVersionId
            """)
    List<Object[]> countAnsweredByQuestionVersionIds(
            @org.springframework.data.repository.query.Param("questionVersionIds") java.util.Collection<UUID> questionVersionIds);

    long countByAttemptId(UUID attemptId);
}
