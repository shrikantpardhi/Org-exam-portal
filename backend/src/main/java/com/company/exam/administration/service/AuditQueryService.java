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
    private final com.company.exam.common.audit.SecurityEventLogger.UserOrganizationLookup lookup;

    public AuditQueryService(AuditLogRepository auditLogRepository,
            com.company.exam.common.audit.SecurityEventLogger.UserOrganizationLookup lookup) {
        this.auditLogRepository = auditLogRepository;
        this.lookup = lookup;
    }

    @Transactional(readOnly = true)
    /**
     * Scoped search (ADR-0046, D-11).
     *
     * <p>Before this, the explorer returned every row of a two-organization deployment to any caller
     * holding {@code AUDIT_LOG_VIEW}. The scope is the caller's organization, plus rows with a NULL
     * organization (platform-wide activity: the system actor, scheduled jobs, workers) only when the
     * caller is the platform tier. An organization ADMIN never sees another organization's history,
     * and never sees platform-wide rows either — those belong to the platform, not to a tenant.
     */
    public Page<AdministrationDtos.AuditLogResponse> search(UUID userId, String entityName, UUID entityId,
            String action, Instant from, Instant to, Pageable pageable, UUID actorId) {
        UUID actorOrganizationId = actorId == null ? null
                : lookup.organizationOf(actorId).orElse(null);
        return auditLogRepository.findAll(
                        specification(userId, entityName, entityId, action, from, to, actorOrganizationId),
                        pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public long countSince(Instant since) {
        return auditLogRepository.countByCreatedOnAfter(since);
    }

    /** Filters exactly as frozen; the actor id is never taken from the client for attribution. */
    private Specification<AuditLog> specification(UUID userId, String entityName, UUID entityId, String action,
            Instant from, Instant to, UUID actorOrganizationId) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            // Organization scope first, and fail closed (G-10): an unattributable caller sees
            // nothing rather than the installation's trail.
            if (actorOrganizationId == null) {
                predicates.add(builder.disjunction());
            } else {
                predicates.add(builder.equal(root.get("organizationId"), actorOrganizationId));
            }
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
