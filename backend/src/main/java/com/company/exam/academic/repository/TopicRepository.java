package com.company.exam.academic.repository;

import com.company.exam.academic.entity.Topic;
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

/** Persistence for {@link Topic} ({@code specs/11-P2-Academic-V1.5.md §25.4}). */
public interface TopicRepository extends JpaRepository<Topic, UUID>, JpaSpecificationExecutor<Topic> {

    Page<Topic> findByChapterId(UUID chapterId, Pageable pageable);

    List<Topic> findByChapterIdAndStatus(UUID chapterId, Status status);

    Optional<Topic> findByChapterIdAndCode(UUID chapterId, String code);

    boolean existsByChapterIdAndCode(UUID chapterId, String code);

    long countByChapterIdAndStatusNot(UUID chapterId, Status status);

    @Query("""
            select t from Topic t
            where t.chapterId = :chapterId
              and (lower(t.code) like lower(concat('%', :term, '%'))
                   or lower(t.name) like lower(concat('%', :term, '%')))
            """)
    Page<Topic> searchByChapterId(@Param("chapterId") UUID chapterId, @Param("term") String term, Pageable pageable);
}
