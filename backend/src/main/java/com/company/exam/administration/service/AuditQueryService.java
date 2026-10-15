package com.company.exam.administration.service;

import com.company.exam.administration.dto.AdministrationDtos;
import com.company.exam.administration.entity.AuditLog;
import com.company.exam.administration.repository.AuditLogRepository;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Audit search ({@code specs/20-P11-Administration-V1.5.md §11.8}).
 *
 * <p>Read-only by construction: the trail is insert-only and no update or delete operation exists
 * ({@code §11.2}).
 */
@Service
public class AuditQueryService {

    private final AuditLogRepository auditLogRepository;

    public AuditQueryService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional(readOnly = true)
    public Page<AdministrationDtos.AuditLogResponse> search(UUID userId, String entityName, UUID entityId,
            String action, Instant from, Instant to, Pageable pageable) {
        return auditLogRepository.findAll(specification(userId, entityName, entityId, action, from, to), pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public long countSince(Instant since) {
        return auditLogRepository.countByCreatedOnAfter(since);
    }

    /** Filters exactly as frozen; the actor id is never taken from the client for attribution. */
    private Specification<AuditLog> specification(UUID userId, String entityName, UUID entityId, String action,
            Instant from, Instant to) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (userId != null) {
                predicates.add(builder.equal(root.get("userId"), userId));
            }
            if (entityName != null && !entityName.isBlank()) {
                predicates.add(builder.equal(root.get("entityName"), entityName));
            }
            if (entityId != null) {
                predicates.add(builder.equal(root.get("entityId"), entityId));
            }
            if (action != null && !action.isBlank()) {
                predicates.add(builder.equal(root.get("action"), action));
            }
            if (from != null) {
                predicates.add(builder.greaterThanOrEqualTo(root.get("createdOn"), from));
            }
            if (to != null) {
                predicates.add(builder.lessThanOrEqualTo(root.get("createdOn"), to));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private AdministrationDtos.AuditLogResponse toResponse(AuditLog auditLog) {
        return new AdministrationDtos.AuditLogResponse(auditLog.getId(), auditLog.getUserId(),
                auditLog.getEntityName(), auditLog.getEntityId(), auditLog.getAction(), auditLog.getRemarks(),
                auditLog.getCreatedOn());
    }
}
