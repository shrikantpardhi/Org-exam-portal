package com.company.exam.identity.repository;

import com.company.exam.identity.entity.LoginSession;
import com.company.exam.identity.entity.SessionStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Persistence for {@link LoginSession} ({@code specs/10-P1-Identity-V1.5.md §14, §133}). */
public interface LoginSessionRepository extends JpaRepository<LoginSession, UUID>,
        JpaSpecificationExecutor<LoginSession> {

    List<LoginSession> findByUserId(UUID userId);

    List<LoginSession> findByUserIdAndSessionStatus(UUID userId, SessionStatus sessionStatus);

    List<LoginSession> findBySessionStatus(SessionStatus sessionStatus);

    Optional<LoginSession> findByIdAndUserId(UUID id, UUID userId);

    /**
     * Marks every ACTIVE session for a user as EXPIRED. Used by the password-reset session
     * invalidation policy ({@code specs/10-P1-Identity-V1.5.md §28}) and the absolute-lifetime
     * timeout sweep ({@code §41}).
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update LoginSession s
            set s.sessionStatus = com.company.exam.identity.entity.SessionStatus.EXPIRED
            where s.userId = :userId
              and s.sessionStatus = com.company.exam.identity.entity.SessionStatus.ACTIVE
            """)
    int expireActiveSessionsForUser(@Param("userId") UUID userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update LoginSession s
            set s.sessionStatus = com.company.exam.identity.entity.SessionStatus.EXPIRED
            where s.sessionStatus = com.company.exam.identity.entity.SessionStatus.ACTIVE
              and s.loginTime < :cutoff
            """)
    int expireSessionsOlderThan(@Param("cutoff") Instant cutoff);
}
