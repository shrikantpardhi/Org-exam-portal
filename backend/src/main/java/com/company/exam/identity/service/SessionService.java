package com.company.exam.identity.service;

import com.company.exam.identity.config.SessionProperties;
import com.company.exam.identity.entity.LoginSession;
import com.company.exam.identity.entity.SessionStatus;
import com.company.exam.identity.repository.LoginSessionRepository;
import com.company.exam.common.audit.SecurityEventLogger;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns login session state transitions ({@code specs/10-P1-Identity-V1.5.md §85}).
 *
 * <p>Transitions are terminal: {@code ACTIVE -> LOGGED_OUT} and {@code ACTIVE -> EXPIRED}. A
 * terminal session is never resurrected. Logout is idempotent ({@code §128}); timeout is an
 * absolute lifetime because the frozen schema has no activity column ({@code §41, §42}).
 */
@Service
public class SessionService {

    private static final int MAX_IP_LENGTH = 100;
    private static final int MAX_DEVICE_LENGTH = 1000;

    private final LoginSessionRepository repository;
    private final SessionProperties properties;
    private final SecurityEventLogger securityEvents;

    public SessionService(LoginSessionRepository repository, SessionProperties properties,
            SecurityEventLogger securityEvents) {
        this.repository = repository;
        this.properties = properties;
        this.securityEvents = securityEvents;
    }

    @Transactional
    public LoginSession create(UUID userId, String ipAddress, String deviceInfo) {
        LoginSession session = new LoginSession();
        session.setUserId(userId);
        session.setLoginTime(Instant.now());
        session.setSessionStatus(SessionStatus.ACTIVE);
        session.setIpAddress(truncate(ipAddress, MAX_IP_LENGTH));
        session.setDeviceInfo(truncate(deviceInfo, MAX_DEVICE_LENGTH));
        return repository.save(session);
    }

    @Transactional(readOnly = true)
    public boolean isActive(UUID sessionId, UUID userId) {
        if (sessionId == null || userId == null) {
            return false;
        }
        return repository.findById(sessionId)
                .filter(session -> session.getSessionStatus() == SessionStatus.ACTIVE)
                .filter(session -> session.getUserId().equals(userId))
                .isPresent();
    }

    /**
     * Resolves the user's {@code must_change_password} flag for an ACTIVE session in one query.
     * The JWT filter uses this instead of a second user lookup so authentication stays a single
     * read per request (ADR-0034).
     *
     * @return the flag when the session is active and belongs to the user; empty otherwise
     */
    @Transactional(readOnly = true)
    public Optional<Boolean> mustChangePasswordForActiveSession(UUID sessionId, UUID userId) {
        if (sessionId == null || userId == null) {
            return Optional.empty();
        }
        return repository.findMustChangePasswordByActiveSession(sessionId, userId);
    }

    @Transactional(readOnly = true)
    public Optional<LoginSession> find(UUID sessionId) {
        return repository.findById(sessionId);
    }

    /**
     * Ends a session. Repeated logout of an already terminal session is a safe no-op.
     *
     * @return {@code true} when the session belongs to the user and the request was accepted
     */
    @Transactional
    public boolean logout(UUID sessionId, UUID userId) {
        if (sessionId == null || userId == null) {
            return false;
        }
        LoginSession session = repository.findById(sessionId).orElse(null);
        if (session == null || !session.getUserId().equals(userId)) {
            return false;
        }
        if (session.getSessionStatus() == SessionStatus.ACTIVE) {
            session.setSessionStatus(SessionStatus.LOGGED_OUT);
            session.setLogoutTime(Instant.now());
            repository.save(session);
            securityEvents.logout(userId, sessionId);
        }
        return true;
    }

    /** Marks a single session EXPIRED. */
    @Transactional
    public void expire(UUID sessionId) {
        repository.findById(sessionId).ifPresent(session -> {
            if (session.getSessionStatus() == SessionStatus.ACTIVE) {
                session.setSessionStatus(SessionStatus.EXPIRED);
                repository.save(session);
                securityEvents.sessionExpired(session.getUserId(), sessionId);
            }
        });
    }

    /** Marks every ACTIVE session of a user EXPIRED (used on password reset and archive). */
    @Transactional
    public int expireAllForUser(UUID userId) {
        return repository.expireActiveSessionsForUser(userId);
    }

    /** Marks every ACTIVE session of a user EXPIRED except the one being kept (change-password). */
    @Transactional
    public int expireAllExceptForUser(UUID userId, UUID sessionId) {
        return repository.expireActiveSessionsForUserExcept(userId, sessionId);
    }

    /** Sweeps sessions whose absolute lifetime has elapsed. */
    @Transactional
    public int expireStaleSessions() {
        Instant cutoff = Instant.now().minus(properties.getAbsoluteLifetime());
        return repository.expireSessionsOlderThan(cutoff);
    }

    @Transactional(readOnly = true)
    public Page<LoginSession> search(UUID userId, SessionStatus status, Instant from, Instant to, Pageable pageable) {
        Specification<LoginSession> specification = (root, query, cb) -> {
            List<Predicate> predicates = new java.util.ArrayList<>();
            if (userId != null) {
                predicates.add(cb.equal(root.get("userId"), userId));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("sessionStatus"), status));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("loginTime"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("loginTime"), to));
            }
            return predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(new Predicate[0]));
        };
        return repository.findAll(specification, pageable);
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        String trimmed = value.strip();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }
}
