package com.company.exam.academic.repository;

import com.company.exam.academic.entity.SubTopic;
import com.company.exam.common.enums.Status;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Persistence for {@link SubTopic} ({@code specs/11-P2-Academic-V1.5.md §25.5}). */
public interface SubTopicRepository extends JpaRepository<SubTopic, UUID>, JpaSpecificationExecutor<SubTopic> {

    Page<SubTopic> findByTopicId(UUID topicId, Pageable pageable);

    List<SubTopic> findByTopicIdAndStatus(UUID topicId, Status status);

    Optional<SubTopic> findByTopicIdAndCode(UUID topicId, String code);

    boolean existsByTopicIdAndCode(UUID topicId, String code);

    long countByTopicIdAndStatusNot(UUID topicId, Status status);

    @Query("""
            select st from SubTopic st
            where st.topicId = :topicId
              and (lower(st.code) like lower(concat('%', :term, '%'))
                   or lower(st.name) like lower(concat('%', :term, '%')))
            """)
    Page<SubTopic> searchByTopicId(@Param("topicId") UUID topicId, @Param("term") String term, Pageable pageable);
}
