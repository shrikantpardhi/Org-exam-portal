package com.company.exam.common.audit;

import java.util.UUID;

/**
 * The shared audit-write boundary ({@code specs/20-P11-Administration-V1.5.md §11.11}).
 *
 * <p>Every module already records business mutations through
 * {@link SecurityEventLogger#audit(UUID, String, String, UUID, String)}. Administration implements
 * this interface to persist those events into the immutable {@code audit_log} table, so the audit
 * trail becomes durable without any module depending on the Administration module and without a
 * second audit mechanism.
 *
 * <p>The implementation participates in the caller's transaction: if audit insertion fails, the
 * business transaction rolls back and no important action happens without audit evidence.
 */
public interface AuditTrail {

    /**
     * One audit entry.
     *
     * @param actorId    the authenticated actor, the system actor, or null when unattributable
     * @param action     a stable business action such as CREATE, UPDATE, PUBLISH, REFUND
     * @param entityName the affected entity type
     * @param entityId   the affected record, when there is a single one
     * @param remarks        a short, secret-free description
     * @param organizationId the organization the event belongs to, or {@code null} when it is
     *                       platform-wide (system actor, scheduler, worker). Recorded at write time
     *                       so the ADMIN explorer can be scoped without deriving it at read time
     *                       (ADR-0046, D-11)
     */
    record Entry(UUID actorId, String action, String entityName, UUID entityId, String remarks,
            UUID organizationId) {
    }

    void record(Entry entry);
}
