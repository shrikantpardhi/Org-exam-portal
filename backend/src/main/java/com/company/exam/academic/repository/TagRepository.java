package com.company.exam.academic.repository;

import com.company.exam.academic.entity.Tag;
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

/** Persistence for {@link Tag} ({@code specs/11-P2-Academic-V1.5.md §25.6}). */
public interface TagRepository extends JpaRepository<Tag, UUID>, JpaSpecificationExecutor<Tag> {

    List<Tag> findByStatus(Status status);

    Optional<Tag> findByCode(String code);

    boolean existsByCode(String code);

    @Query("""
            select t from Tag t
            where lower(t.code) like lower(concat('%', :term, '%'))
               or lower(t.name) like lower(concat('%', :term, '%'))
            """)
    Page<Tag> searchByCodeOrName(@Param("term") String term, Pageable pageable);
}
