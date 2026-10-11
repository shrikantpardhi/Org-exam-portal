package com.company.exam.academic.repository;

import com.company.exam.academic.entity.Chapter;
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

/** Persistence for {@link Chapter} ({@code specs/11-P2-Academic-V1.5.md §25.3}). */
public interface ChapterRepository extends JpaRepository<Chapter, UUID>, JpaSpecificationExecutor<Chapter> {

    Page<Chapter> findBySubjectId(UUID subjectId, Pageable pageable);

    List<Chapter> findBySubjectIdAndStatus(UUID subjectId, Status status);

    Optional<Chapter> findBySubjectIdAndCode(UUID subjectId, String code);

    boolean existsBySubjectIdAndCode(UUID subjectId, String code);

    long countBySubjectIdAndStatusNot(UUID subjectId, Status status);

    @Query("""
            select c from Chapter c
            where c.subjectId = :subjectId
              and (lower(c.code) like lower(concat('%', :term, '%'))
                   or lower(c.name) like lower(concat('%', :term, '%')))
            """)
    Page<Chapter> searchBySubjectId(@Param("subjectId") UUID subjectId, @Param("term") String term,
            Pageable pageable);
}
