package com.company.exam.identity.service;

import com.company.exam.common.api.ApiError;
import com.company.exam.common.enums.Status;
import com.company.exam.common.exception.BusinessException;
import com.company.exam.identity.IdentityConstants;
import com.company.exam.identity.entity.User;
import com.company.exam.identity.repository.UserRepository;
import com.company.exam.common.audit.SecurityEventLogger;
import com.company.exam.identity.validation.IdentityNormalizer;
import com.company.exam.identity.validation.PasswordPolicy;
import java.util.List;
import java.util.Optional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Password reset workflows ({@code specs/10-P1-Identity-V1.5.md §24-§28, §130}).
 *
 * <p>The forgot-password response never reveals whether an account exists ({@code §25}). The reset
 * token is single-use and short-lived without a token table ({@code §24, §27}); see
 * {@link PasswordResetTokenService}. A successful reset replaces only the password hash and
 * invalidates the user's sessions.
 */
@Service
public class PasswordResetService {

    private static final String GENERIC_INVALID = "Invalid or expired reset token";

    private final UserRepository userRepository;
    private final PasswordResetTokenService tokenService;
    private final PasswordResetDelivery delivery;
    private final PasswordPolicy passwordPolicy;
    private final PasswordEncoder passwordEncoder;
    private final IdentityNormalizer normalizer;
    private final SessionService sessionService;
    private final AuthorizationService authorizationService;
    private final SecurityEventLogger securityEvents;

    public PasswordResetService(UserRepository userRepository, PasswordResetTokenService tokenService,
            PasswordResetDelivery delivery, PasswordPolicy passwordPolicy, PasswordEncoder passwordEncoder,
            IdentityNormalizer normalizer, SessionService sessionService, AuthorizationService authorizationService,
            SecurityEventLogger securityEvents) {
        this.userRepository = userRepository;
        this.tokenService = tokenService;
        this.delivery = delivery;
        this.passwordPolicy = passwordPolicy;
        this.passwordEncoder = passwordEncoder;
        this.normalizer = normalizer;
        this.sessionService = sessionService;
        this.authorizationService = authorizationService;
        this.securityEvents = securityEvents;
    }

    /**
     * Requests a reset. Always completes without indicating whether the account exists.
     */
    @Transactional
    public void requestReset(String email) {
        Optional<User> candidate = normalizeQuietly(email).flatMap(userRepository::findByEmail);
        candidate.filter(user -> !isSystemActor(user))
                .filter(user -> user.getStatus() == Status.ACTIVE)
                .ifPresent(user -> {
                    String token = tokenService.issue(user);
                    delivery.deliver(user, token);
                    securityEvents.passwordResetRequested(user.getId());
                });
    }

    /**
     * Issues and delivers a reset link for a freshly created account so its owner can choose a
     * first password (ADR-0034). Unlike {@link #requestReset}, this is driven by the create-user
     * workflow, not by a self-service request, and takes the already-persisted user directly.
     */
    @Transactional
    public void deliverFirstPasswordInvitation(User user) {
        String token = tokenService.issue(user);
        delivery.deliver(user, token);
    }

    /**
     * Completes a reset. Requires a valid, unexpired, unused token and a policy-compliant password.
     */
    @Transactional
    public void reset(String rawToken, String newPassword) {
        PasswordResetTokenService.ResetToken token = tokenService.parse(rawToken)
                .orElseThrow(this::invalidToken);
        User user = userRepository.findByIdForUpdate(token.userId())
                .filter(candidate -> !isSystemActor(candidate))
                .filter(candidate -> candidate.getStatus() == Status.ACTIVE)
                .orElseThrow(this::invalidToken);
        if (!tokenService.matches(token, user)) {
            throw invalidToken();
        }
        passwordPolicy.validate(newPassword, user.getEmail());
        user.setPassword(passwordEncoder.encode(newPassword));
        // The owner has now chosen their own password, so the forced first-login change no
        // longer applies (ADR-0034).
        user.setMustChangePassword(false);
        userRepository.save(user);
        sessionService.expireAllForUser(user.getId());
        authorizationService.evictAfterCommit(user.getId());
        securityEvents.passwordResetSucceeded(user.getId());
    }

    private Optional<String> normalizeQuietly(String email) {
        try {
            return Optional.ofNullable(normalizer.normalizeEmail(email));
        } catch (RuntimeException invalidFormat) {
            return Optional.empty();
        }
    }

    private boolean isSystemActor(User user) {
        return IdentityConstants.SYSTEM_ACTOR_ID.equals(user.getId())
                || IdentityConstants.SYSTEM_ACTOR_EMAIL.equalsIgnoreCase(user.getEmail());
    }

    private BusinessException invalidToken() {
        return new BusinessException(GENERIC_INVALID,
                List.of(ApiError.of("token", "INVALID_RESET_TOKEN", GENERIC_INVALID)));
    }
}
