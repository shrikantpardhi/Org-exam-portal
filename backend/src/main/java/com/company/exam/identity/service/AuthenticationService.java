package com.company.exam.identity.service;

import com.company.exam.common.api.ApiError;
import com.company.exam.common.enums.Status;
import com.company.exam.common.exception.AuthenticationException;
import com.company.exam.common.exception.ValidationException;
import com.company.exam.identity.IdentityConstants;
import com.company.exam.identity.dto.AuthDtos;
import com.company.exam.identity.entity.LoginSession;
import com.company.exam.identity.exception.PortalMismatchException;
import com.company.exam.identity.entity.Organization;
import com.company.exam.identity.entity.User;
import com.company.exam.identity.repository.OrganizationRepository;
import com.company.exam.identity.repository.RoleRepository;
import com.company.exam.identity.repository.UserRepository;
import com.company.exam.identity.security.IdentityPrincipal;
import com.company.exam.identity.security.JwtTokenIssuer;
import com.company.exam.common.audit.SecurityEventLogger;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Authentication workflows ({@code specs/10-P1-Identity-V1.5.md §16, §81}).
 *
 * <p>Coordinates credential verification, login session establishment, access-token issuance,
 * refresh, and logout. It never exposes repositories to controllers.
 *
 * <p>Enumerating failures are indistinguishable: an unknown account, a wrong password, an inactive
 * user, an inactive organization, and the system actor all return the same generic 401
 * ({@code §19, §15}).
 */
@Service
public class AuthenticationService {

    private static final String GENERIC_MESSAGE = "Invalid credentials";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final OrganizationRepository organizationRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenIssuer tokenIssuer;
    private final SessionService sessionService;
    private final SecurityEventLogger securityEvents;

    public AuthenticationService(UserRepository userRepository, RoleRepository roleRepository,
            OrganizationRepository organizationRepository, PasswordEncoder passwordEncoder,
            JwtTokenIssuer tokenIssuer, SessionService sessionService, SecurityEventLogger securityEvents) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.organizationRepository = organizationRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenIssuer = tokenIssuer;
        this.sessionService = sessionService;
        this.securityEvents = securityEvents;
    }

    @Transactional
    public AuthDtos.LoginResponse login(AuthDtos.LoginRequest request, String ipAddress, String deviceInfo) {
        String identifier = request.username() == null ? "" : request.username().trim().toLowerCase();
        Optional<User> candidate = userRepository.findByEmail(identifier);
        User user = candidate.orElse(null);

        if (user == null) {
            // Constant-ish work to reduce the timing side channel that would reveal account existence.
            passwordEncoder.matches(request.password(), passwordEncoder.encode("no-such-account"));
            securityEvents.loginFailed(identifier, "UNKNOWN_ACCOUNT");
            throw genericFailure();
        }
        if (isSystemActor(user) || user.getStatus() != Status.ACTIVE) {
            securityEvents.loginFailed(identifier, "ACCOUNT_NOT_ELIGIBLE");
            throw genericFailure();
        }
        Organization organization = organizationRepository.findById(user.getOrganizationId()).orElse(null);
        if (organization == null || organization.getStatus() != Status.ACTIVE) {
            securityEvents.loginFailed(identifier, "ORGANIZATION_INACTIVE");
            throw genericFailure();
        }
        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            securityEvents.loginFailed(identifier, "BAD_CREDENTIALS");
            throw genericFailure();
        }

        List<String> roles = roleRepository.findEffectiveRoleCodes(user.getId());
        validatePortal(request.portal(), roles);
        LoginSession session = sessionService.create(user.getId(), ipAddress, deviceInfo);
        JwtTokenIssuer.IssuedToken token = tokenIssuer.issue(user.getId(), session.getId());
        securityEvents.loginSucceeded(user.getId());
        return new AuthDtos.LoginResponse(token.token(), "Bearer", token.expiresInSeconds(),
                new AuthDtos.LoginUser(user.getId(), user.getFirstName(), user.getLastName(), roles,
                        user.isMustChangePassword()));
    }

    @Transactional
    public void logout(IdentityPrincipal principal) {
        if (principal != null && principal.sessionId() != null) {
            sessionService.logout(principal.sessionId(), principal.userId());
        }
    }

    /**
     * Reissues an access token for an already-authenticated session
     * ({@code specs/10-P1-Identity-V1.5.md §31}).
     *
     * <p>The frozen schema has no refresh-token column and P1 must not add one ({@code §42}), so
     * refresh is bound to the current active login session rather than a separate credential. It
     * therefore refreshes a still-valid session; it cannot revive an expired token. This is
     * recorded in {@code docs/decisions/ADR-0011-p1-refresh-token-strategy.md}.
     */
    @Transactional
    public AuthDtos.LoginResponse refresh(IdentityPrincipal principal) {
        if (principal == null || principal.sessionId() == null) {
            throw genericFailure();
        }
        if (!sessionService.isActive(principal.sessionId(), principal.userId())) {
            throw genericFailure();
        }
        User user = userRepository.findById(principal.userId()).orElseThrow(this::genericFailure);
        if (user.getStatus() != Status.ACTIVE || isSystemActor(user)) {
            throw genericFailure();
        }
        List<String> roles = roleRepository.findEffectiveRoleCodes(user.getId());
        JwtTokenIssuer.IssuedToken token = tokenIssuer.issue(user.getId(), principal.sessionId());
        return new AuthDtos.LoginResponse(token.token(), "Bearer", token.expiresInSeconds(),
                new AuthDtos.LoginUser(user.getId(), user.getFirstName(), user.getLastName(), roles,
                        user.isMustChangePassword()));
    }

    private boolean isSystemActor(User user) {
        return IdentityConstants.SYSTEM_ACTOR_ID.equals(user.getId())
                || IdentityConstants.SYSTEM_ACTOR_EMAIL.equalsIgnoreCase(user.getEmail());
    }

    private AuthenticationException genericFailure() {
        return new AuthenticationException(GENERIC_MESSAGE);
    }

    private void validatePortal(String portal, List<String> roles) {
        if (portal == null || portal.isBlank()) {
            return;
        }
        String normalized = portal.trim().toUpperCase(Locale.ROOT);
        String requiredRole = switch (normalized) {
            case "SYSTEM_ADMIN" -> IdentityConstants.SUPER_ADMIN_ROLE_CODE;
            case "ORGANIZATION_ADMIN" -> IdentityConstants.ADMIN_ROLE_CODE;
            case "STUDENT" -> "STUDENT";
            default -> throw new ValidationException("Validation failed",
                    List.of(ApiError.of("portal", "INVALID_PORTAL",
                            "portal must be one of SYSTEM_ADMIN, ORGANIZATION_ADMIN, STUDENT")));
        };
        if (!roles.contains(requiredRole)) {
            throw new PortalMismatchException();
        }
    }
}
