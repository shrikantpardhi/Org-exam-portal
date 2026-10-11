package com.company.exam.identity.dto;

import com.company.exam.identity.entity.SessionStatus;
import java.time.Instant;
import java.util.UUID;

/**
 * Login session DTOs ({@code specs/10-P1-Identity-V1.5.md §112, §115}).
 *
 * <p>Only safe session metadata is exposed. Authentication tokens and cryptographic material are
 * never returned.
 */
public final class SessionDtos {

    private SessionDtos() {
    }

    public record LoginSessionSummary(
            UUID id,
            UUID userId,
            Instant loginTime,
            Instant logoutTime,
            SessionStatus sessionStatus,
            String ipAddress,
            String deviceInfo) {
    }

    public record LoginSessionDetail(
            UUID id,
            UUID userId,
            Instant loginTime,
            Instant logoutTime,
            SessionStatus sessionStatus,
            String ipAddress,
            String deviceInfo) {
    }
}
