package com.company.exam.academic.repository;

import com.company.exam.academic.entity.Subject;
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

/** Persistence for {@link Subject} ({@code specs/11-P2-Academic-V1.5.md §25.2}). */
public interface SubjectRepository extends JpaRepository<Subject, UUID>, JpaSpecificationExecutor<Subject> {

    Page<Subject> findByExamId(UUID examId, Pageable pageable);

    List<Subject> findByExamIdAndStatus(UUID examId, Status status);

    Optional<Subject> findByExamIdAndCode(UUID examId, String code);

    boolean existsByExamIdAndCode(UUID examId, String code);

    /** Non-archived subjects of an exam: the descendants that block archiving the exam. */
    long countByExamIdAndStatusNot(UUID examId, Status status);

    @Query("""
            select s from Subject s
            where s.examId = :examId
              and (lower(s.code) like lower(concat('%', :term, '%'))
                   or lower(s.name) like lower(concat('%', :term, '%')))
            """)
    Page<Subject> searchByExamId(@Param("examId") UUID examId, @Param("term") String term, Pageable pageable);
}
