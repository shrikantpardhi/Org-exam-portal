package com.company.exam.academic.repository;

import com.company.exam.academic.entity.Language;
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

/** Persistence for {@link Language} ({@code specs/11-P2-Academic-V1.5.md §25.6}). */
public interface LanguageRepository extends JpaRepository<Language, UUID>, JpaSpecificationExecutor<Language> {

    List<Language> findByStatus(Status status);

    Optional<Language> findByCode(String code);

    boolean existsByCode(String code);

    @Query("""
            select l from Language l
            where lower(l.code) like lower(concat('%', :term, '%'))
               or lower(l.name) like lower(concat('%', :term, '%'))
            """)
    Page<Language> searchByCodeOrName(@Param("term") String term, Pageable pageable);
}
