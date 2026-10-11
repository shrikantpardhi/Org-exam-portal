package com.company.exam.academic.repository;

import com.company.exam.academic.entity.Exam;
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

/** Persistence for {@link Exam} ({@code specs/11-P2-Academic-V1.5.md §25.1}). */
public interface ExamRepository extends JpaRepository<Exam, UUID>, JpaSpecificationExecutor<Exam> {

    Optional<Exam> findByCode(String code);

    boolean existsByCode(String code);

    List<Exam> findByStatus(Status status);

    @Query("""
            select e from Exam e
            where lower(e.code) like lower(concat('%', :term, '%'))
               or lower(e.name) like lower(concat('%', :term, '%'))
            """)
    Page<Exam> search(@Param("term") String term, Pageable pageable);
}
