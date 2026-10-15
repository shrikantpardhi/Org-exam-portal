package com.company.exam.common.audit;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Emits sanitized security and audit events, shared by all modules
 * ({@code specs/10-P1-Identity-V1.5.md §93, §94}, {@code specs/11-P2-Academic-V1.5.md §29}).
 *
 * <p>Only safe metadata is logged: the actor, the operation, the entity, and the correlation id.
 * Passwords, tokens, authorization headers, and full request bodies are never logged.
 *
 * <p>The immutable {@code audit_log} table is delivered by the Administration phase; this logger
 * records into it through the {@link AuditTrail} boundary when an implementation is present
 * (Administration implements it, and the insert participates in the business transaction). The
 * structured log line remains as the operational/security trail.
 */
@Component
public class SecurityEventLogger {

    private static final Logger log = LoggerFactory.getLogger("SECURITY");

    /**
     * The optional durable audit boundary. Absent in slices without the Administration module, and
     * absent in plain unit tests constructed with {@code new SecurityEventLogger()}; the structured
     * log line is always emitted.
     */
    @Autowired(required = false)
    private AuditTrail auditTrail;

    /**
     * Generic audit event for a business mutation ({@code specs/11-P2-Academic-V1.5.md §29}).
     *
     * @param actorId    the authenticated actor, or the system actor
     * @param action     CREATE / UPDATE / ARCHIVE / DEACTIVATE
     * @param entityName the entity type
     * @param entityId   the affected record
     * @param remarks    a short, secret-free description
     */
    public void audit(UUID actorId, String action, String entityName, UUID entityId, String remarks) {
        log.info("security event={} actorId={} entityName={} entityId={} remarks={}",
                action, actorId, entityName, entityId, remarks);
        AuditTrail trail = this.auditTrail;
        if (trail != null) {
            // Participates in the caller's transaction; a failure rolls the business mutation back (§11.11).
            trail.record(new AuditTrail.Entry(actorId, action, entityName, entityId, remarks));
        }
    }

    public void loginSucceeded(UUID userId) {
        log.info("security event=LOGIN_SUCCESS userId={}", userId);
    }

    public void loginFailed(String username, String reason) {
        log.info("security event=LOGIN_FAILURE username={} reason={}", mask(username), reason);
    }

    public void logout(UUID userId, UUID sessionId) {
        log.info("security event=LOGOUT userId={} sessionId={}", userId, sessionId);
    }

    public void sessionExpired(UUID userId, UUID sessionId) {
        log.info("security event=SESSION_EXPIRED userId={} sessionId={}", userId, sessionId);
    }

    public void passwordResetRequested(UUID userId) {
        log.info("security event=PASSWORD_RESET_REQUEST userId={}", userId);
    }

    public void passwordResetSucceeded(UUID userId) {
        log.info("security event=PASSWORD_RESET_SUCCESS userId={}", userId);
    }

    public void roleChanged(UUID actorId, UUID targetUserId, String action, String detail) {
        log.info("security event={} actorId={} targetUserId={} detail={}", action, actorId, targetUserId, detail);
    }

    private String mask(String value) {
        if (value == null || value.isBlank()) {
            return "<absent>";
        }
        int at = value.indexOf('@');
        if (at <= 1) {
            return "***";
        }
        return value.charAt(0) + "***" + value.substring(at);
    }
}
