package com.company.exam.administration.repository;

import com.company.exam.administration.entity.Lookup;
import com.company.exam.common.enums.Status;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence for {@link Lookup} ({@code specs/20-P11 §10}). */
public interface LookupRepository extends JpaRepository<Lookup, UUID> {

    Optional<Lookup> findByCategoryAndCode(String category, String code);

    boolean existsByCategoryAndCode(String category, String code);

    List<Lookup> findByCategoryOrderByDisplayOrderAsc(String category);

    List<Lookup> findByCategoryAndStatusOrderByDisplayOrderAsc(String category, Status status);
}
