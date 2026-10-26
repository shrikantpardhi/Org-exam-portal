package com.company.exam.identity.service;

import com.company.exam.common.enums.Status;
import com.company.exam.common.exception.AuthenticationException;
import com.company.exam.identity.IdentityConstants;
import com.company.exam.identity.dto.AuthDtos;
import com.company.exam.identity.entity.User;
import com.company.exam.identity.repository.UserRepository;
import com.company.exam.common.audit.SecurityEventLogger;
import com.company.exam.identity.validation.PasswordPolicy;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Authenticated password change ({@code specs/05-API-Contract-V1.5.md §72.1}; ADR-0034).
 *
 * <p>This is the forced first-login path for a {@code must_change_password} account and the
 * voluntary rotation path from the avatar menu. It verifies the current password, enforces the
 * platform {@link PasswordPolicy}, clears {@code must_change_password}, keeps the caller's own
 * session alive while expiring every other session, and audits the change.
 */
@Service
public class PasswordChangeService {

    private static final String GENERIC_MESSAGE = "Current password is incorrect";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final SessionService sessionService;
    private final AuthorizationService authorizationService;
    private final SecurityEventLogger securityEvents;

    public PasswordChangeService(UserRepository userRepository, PasswordEncoder passwordEncoder,
            PasswordPolicy passwordPolicy, SessionService sessionService,
            AuthorizationService authorizationService, SecurityEventLogger securityEvents) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.sessionService = sessionService;
        this.authorizationService = authorizationService;
        this.securityEvents = securityEvents;
    }

    /**
     * Changes the caller's password.
     *
     * @param userId    the authenticated caller
     * @param sessionId the caller's login session, kept alive while all others expire
     */
    @Transactional
    public void changePassword(UUID userId, UUID sessionId, AuthDtos.ChangePasswordRequest request) {
        User user = userRepository.findByIdForUpdate(userId)
                .filter(candidate -> candidate.getStatus() == Status.ACTIVE)
                .filter(candidate -> !isSystemActor(candidate))
                .orElseThrow(() -> new AuthenticationException(GENERIC_MESSAGE));
        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            securityEvents.loginFailed(user.getEmail(), "CHANGE_PASSWORD_BAD_CREDENTIALS");
            throw new AuthenticationException(GENERIC_MESSAGE);
        }
        passwordPolicy.validate(request.newPassword(), user.getEmail());
        user.setPassword(passwordEncoder.encode(request.newPassword()));
        user.setMustChangePassword(false);
        userRepository.save(user);
        // Keep the caller's own session; expire every other active session (§72.1).
        sessionService.expireAllExceptForUser(user.getId(), sessionId);
        authorizationService.evictAfterCommit(user.getId());
        securityEvents.audit(userId, "PASSWORD_CHANGE", "User", userId, "Password changed by the account owner");
    }

    private boolean isSystemActor(User user) {
        return IdentityConstants.SYSTEM_ACTOR_ID.equals(user.getId())
                || IdentityConstants.SYSTEM_ACTOR_EMAIL.equalsIgnoreCase(user.getEmail());
    }
}
