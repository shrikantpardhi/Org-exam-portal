package com.company.exam.administration.repository;

import com.company.exam.administration.entity.AuditLog;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * Read and insert access to the audit trail ({@code specs/20-P11 §11.2, §11.8}).
 *
 * <p>There is intentionally no update or delete method: the trail is insert-only and no generic
 * audit update/delete endpoint exists.
 */
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID>,
        JpaSpecificationExecutor<AuditLog> {

    Page<AuditLog> findByEntityNameAndEntityId(String entityName, UUID entityId, Pageable pageable);

    long countByCreatedOnAfter(Instant since);
}
