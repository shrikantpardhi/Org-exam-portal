package com.company.exam.platform.service;

import com.company.exam.common.audit.SecurityEventLogger;
import com.company.exam.common.enums.Status;
import com.company.exam.common.exception.BusinessException;
import com.company.exam.common.exception.NotFoundException;
import com.company.exam.identity.IdentityConstants;
import com.company.exam.identity.entity.Organization;
import com.company.exam.identity.entity.User;
import com.company.exam.identity.entity.UserRole;
import com.company.exam.identity.exception.SuperAdminImmutabilityException;
import com.company.exam.identity.repository.OrganizationRepository;
import com.company.exam.identity.repository.RoleRepository;
import com.company.exam.identity.repository.UserRepository;
import com.company.exam.identity.repository.UserRoleRepository;
import com.company.exam.identity.service.AuthorizationService;
import com.company.exam.identity.service.PasswordResetService;
import com.company.exam.identity.service.SessionService;
import com.company.exam.platform.dto.PlatformDtos;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * SUPER_ADMIN recovery for a locked-out administrator (ADR-0042, D-06).
 *
 * <p>{@code specs/10-P1-Identity-V1.5.md} §63 originally placed administrator recovery outside the
 * API: "a documented direct database procedure operated by a DBA under change control, not a UI
 * feature". That decision was taken when the frozen model allowed several ADMINs per organization,
 * so a locked-out ADMIN could be replaced. ADR-0032 capped the role at one per organization
 * ({@code MAX_ADMINS_PER_ORG = 1}), which removed the replacement path and left a database session
 * as the only recovery. ADR-0042 supersedes the sentence for this narrow case.
 *
 * <p>Deliberately narrow. The service <strong>issues a reset token</strong>; it never returns, sets,
 * or discloses a password. It also cannot grant roles: an operator that could recover an account
 * <em>and</em> re-permission it would be a second, quieter path to the escalation ADR-0040 closes.
 */
@Service
public class PlatformAdminRecoveryService {

    private static final String ADMIN_ROLE_CODE = IdentityConstants.ADMIN_ROLE_CODE;

    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final RoleRepository roleRepository;
    private final PasswordResetService passwordResetService;
    private final SessionService sessionService;
    private final AuthorizationService authorizationService;
    private final SecurityEventLogger securityEvents;

    public PlatformAdminRecoveryService(OrganizationRepository organizationRepository,
            UserRepository userRepository, UserRoleRepository userRoleRepository, RoleRepository roleRepository,
            PasswordResetService passwordResetService, SessionService sessionService,
            AuthorizationService authorizationService, SecurityEventLogger securityEvents) {
        this.organizationRepository = organizationRepository;
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
        this.roleRepository = roleRepository;
        this.passwordResetService = passwordResetService;
        this.sessionService = sessionService;
        this.authorizationService = authorizationService;
        this.securityEvents = securityEvents;
    }

    /**
     * Issues and delivers a single-use password reset for an administrator of a business
     * organization. The account is forced to change its password on the next successful login.
     *
     * @throws NotFoundException when the organization or the user is not in it, or is the PLATFORM
     *     system organization — the platform organization is not a tenant and has no recoverable
     *     administrator
     * @throws BusinessException when the target does not hold the ADMIN role
     * @throws SuperAdminImmutabilityException when the target is the installation owner
     */
    @Transactional
    public PlatformDtos.AdminRecoveryResponse resetAdminPassword(UUID organizationId, UUID userId,
            UUID actorId) {
        User target = requireRecoverableAdmin(organizationId, userId);

        // The invitation path issues a fresh token and delivers it through the configured channel
        // (ADR-0034). Reusing it keeps one delivery mechanism rather than adding a second.
        target.setMustChangePassword(true);
        userRepository.save(target);
        passwordResetService.deliverFirstPasswordInvitation(target);

        // A recovery is also a containment action: an attacker holding the old credential is
        // logged out. Evicting the authorization cache keeps the two stores consistent.
        int revoked = sessionService.expireAllForUser(target.getId());
        authorizationService.evictAfterCommit(target.getId());
        securityEvents.audit(actorId, "ADMIN_RECOVER", "User", target.getId(),
                "Issued a password reset and revoked " + revoked + " session(s)");

        return new PlatformDtos.AdminRecoveryResponse(target.getId(), target.getEmail(),
                PlatformDtos.AdminRecoveryResponse.Action.RESET_PASSWORD, revoked);
    }

    /**
     * Revokes every active session of an administrator without touching the credential. Used when
     * the account is believed compromised but the password is still known to its owner.
     */
    @Transactional
    public PlatformDtos.AdminRecoveryResponse unlockAdminSessions(UUID organizationId, UUID userId,
            UUID actorId) {
        User target = requireRecoverableAdmin(organizationId, userId);

        int revoked = sessionService.expireAllForUser(target.getId());
        authorizationService.evictAfterCommit(target.getId());
        securityEvents.audit(actorId, "ADMIN_UNLOCK", "User", target.getId(),
                "Revoked " + revoked + " session(s)");

        return new PlatformDtos.AdminRecoveryResponse(target.getId(), target.getEmail(),
                PlatformDtos.AdminRecoveryResponse.Action.UNLOCK_SESSIONS, revoked);
    }

    /**
     * Resolves a user that the platform tier may recover, or refuses with a specific reason.
     *
     * <p>Every failure returns 404 for an organization or membership mismatch, so the endpoint does
     * not confirm the existence of a user in another organization (the same non-disclosure rule the
     * forgot-password flow follows, {@code §25}).
     */
    private User requireRecoverableAdmin(UUID organizationId, UUID userId) {
        Organization organization = organizationRepository.findById(organizationId)
                .orElseThrow(() -> NotFoundException.of("Organization", organizationId));
        if (PlatformOrganizationService.PLATFORM_CODE.equalsIgnoreCase(organization.getCode())) {
            throw NotFoundException.of("Organization", organizationId);
        }
        User user = userRepository.findById(userId)
                .filter(candidate -> organizationId.equals(candidate.getOrganizationId()))
                .orElseThrow(() -> NotFoundException.of("User", userId));
        if (user.getStatus() == Status.ARCHIVED) {
            throw new BusinessException("An archived user cannot be recovered",
                    java.util.List.of(com.company.exam.common.api.ApiError.of("userId",
                            "USER_ARCHIVED", "An archived user cannot be recovered")));
        }
        // Super-admin first: the installation owner's account is a different invariant and must
        // not be reported as "not an administrator", which would be a misleading reason.
        if (isSuperAdmin(userId)) {
            throw new SuperAdminImmutabilityException();
        }
        if (!holdsAdminRole(userId)) {
            throw new BusinessException("The user does not hold the ADMIN role",
                    java.util.List.of(com.company.exam.common.api.ApiError.of("userId",
                            "NOT_AN_ADMIN", "The user does not hold the ADMIN role")));
        }
        return user;
    }

    private boolean holdsAdminRole(UUID userId) {
        return roleRepository.findByCodeAndOrganizationIdIsNull(ADMIN_ROLE_CODE)
                .map(role -> userRoleRepository.findByUserIdAndRoleId(userId, role.getId())
                        .filter(userRole -> userRole.getStatus() == Status.ACTIVE)
                        .isPresent())
                .orElse(false);
    }

    private boolean isSuperAdmin(UUID userId) {
        return roleRepository.findByCodeAndOrganizationIdIsNull(IdentityConstants.SUPER_ADMIN_ROLE_CODE)
                .map(role -> userRoleRepository.findByUserIdAndRoleId(userId, role.getId())
                        .filter(userRole -> userRole.getStatus() == Status.ACTIVE)
                        .isPresent())
                .orElse(false);
    }
}
