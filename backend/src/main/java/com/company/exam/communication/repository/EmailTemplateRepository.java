package com.company.exam.communication.repository;

import com.company.exam.communication.entity.EmailTemplate;
import com.company.exam.common.enums.Status;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Persistence for {@link EmailTemplate} ({@code specs/19-P10-Communication-V1.5.md §7.2, §23}). */
public interface EmailTemplateRepository extends JpaRepository<EmailTemplate, UUID>,
        JpaSpecificationExecutor<EmailTemplate> {

    Optional<EmailTemplate> findByCode(String code);

    boolean existsByCode(String code);

    List<EmailTemplate> findByStatus(Status status);
}
