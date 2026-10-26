package com.company.exam.administration.service;

import com.company.exam.administration.entity.AuditLog;
import com.company.exam.administration.repository.AuditLogRepository;
import com.company.exam.common.audit.AuditTrail;
import java.time.Instant;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists the shared audit trail into the immutable {@code audit_log} table
 * ({@code specs/20-P11-Administration-V1.5.md §11}).
 *
 * <p>Insertion uses {@code REQUIRED} so it joins the business transaction: the audit row and the
 * business mutation commit or roll back together ({@code §11.11}). The entity is mapped immutable
 * and there is no update or delete path ({@code §11.2}).
 */
@Service
public class AuditTrailService implements AuditTrail {

    private static final int MAX_ENTITY_NAME = 150;
    private static final int MAX_ACTION = 100;

    private final AuditLogRepository auditLogRepository;

    /**
     * {@code @Lazy} because the shared {@code SecurityEventLogger} depends on this boundary while this
     * service depends on the repository only; laziness keeps context start-up free of a cycle.
     */
    public AuditTrailService(@Lazy AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void record(Entry entry) {
        if (entry == null) {
            return;
        }
        AuditLog auditLog = new AuditLog();
        auditLog.setUserId(entry.actorId());
        auditLog.setEntityName(truncate(entry.entityName(), MAX_ENTITY_NAME));
        auditLog.setEntityId(entry.entityId());
        auditLog.setAction(truncate(entry.action(), MAX_ACTION));
        auditLog.setRemarks(entry.remarks());
        // Written, not derived (ADR-0046, D-11): derivation at read time reports nothing for entities
        // that have no user, and this write is the only point where the actor's organization is known
        // without a join.
        auditLog.setOrganizationId(entry.organizationId());
        auditLog.setCreatedOn(Instant.now());
        auditLogRepository.saveAndFlush(auditLog);
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
