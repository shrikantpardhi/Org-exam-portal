package com.company.exam.identity.bootstrap;

import com.company.exam.common.audit.SecurityEventLogger;
import com.company.exam.common.enums.Status;
import com.company.exam.common.exception.BusinessException;
import com.company.exam.identity.IdentityConstants;
import com.company.exam.identity.entity.Role;
import com.company.exam.identity.entity.User;
import com.company.exam.identity.entity.UserRole;
import com.company.exam.identity.repository.OrganizationRepository;
import com.company.exam.identity.repository.RoleRepository;
import com.company.exam.identity.repository.UserRepository;
import com.company.exam.identity.repository.UserRoleRepository;
import com.company.exam.identity.validation.IdentityNormalizer;
import com.company.exam.identity.validation.PasswordPolicy;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the installation owner (the single SUPER_ADMIN) exactly once (ADR-0032, ADR-0033).
 *
 * <p>The SUPER_ADMIN is the platform owner created during installation, not a business-org ADMIN and
 * not a runtime-created account. It lives in the {@code PLATFORM} system organization, holds the
 * {@code SUPER_ADMIN} role, and starts with {@code must_change_password = true} so the operator must
 * replace the bootstrap password on first login (ADR-0034).
 *
 * <p><b>No endpoint.</b> Invoked in-process by {@link SuperAdminBootstrapCli}, which boots a non-web
 * context, reads the credential from stdin, and exits. With {@code app.super-admin.bootstrap.enabled}
 * absent the bean is not created at all. The credential is hashed with the platform encoder (never
 * stored or logged in plaintext), enforced against {@link PasswordPolicy}, attributed to the system
 * actor, and audited. Re-running is idempotent: an existing SUPER_ADMIN is reported and left untouched.
 */
@Service
@ConditionalOnProperty(name = "app.super-admin.bootstrap.enabled", havingValue = "true")
public class SuperAdminBootstrapService {

    static final String SUPER_ADMIN_ROLE_CODE = "SUPER_ADMIN";
    static final String PLATFORM_ORG_CODE = "PLATFORM";

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final RoleRepository roleRepository;
    private final OrganizationRepository organizationRepository;
    private final IdentityNormalizer normalizer;
    private final PasswordPolicy passwordPolicy;
    private final PasswordEncoder passwordEncoder;
    private final SecurityEventLogger securityEvents;
    private final SuperAdminBootstrapProperties properties;
    private final Environment environment;

    public SuperAdminBootstrapService(UserRepository userRepository, UserRoleRepository userRoleRepository,
            RoleRepository roleRepository, OrganizationRepository organizationRepository,
            IdentityNormalizer normalizer, PasswordPolicy passwordPolicy, PasswordEncoder passwordEncoder,
            SecurityEventLogger securityEvents, SuperAdminBootstrapProperties properties,
            Environment environment) {
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
        this.roleRepository = roleRepository;
        this.organizationRepository = organizationRepository;
        this.normalizer = normalizer;
        this.passwordPolicy = passwordPolicy;
        this.passwordEncoder = passwordEncoder;
        this.securityEvents = securityEvents;
        this.properties = properties;
        this.environment = environment;
    }

    /** The environment reported by configuration ({@code app.environment}). */
    public String currentEnvironment() {
        return environment.getProperty("app.environment", "unknown");
    }

    /** Fails closed unless the environment permits bootstrap. */
    public void assertEnvironmentAllowsBootstrap() {
        String current = currentEnvironment();
        if (current.equalsIgnoreCase(properties.getForbiddenEnvironment())) {
            throw new SuperAdminBootstrapDisabledException(
                    "SUPER_ADMIN bootstrap is not permitted in the '" + current
                            + "' environment; the installation owner must be provisioned by a controlled step");
        }
        for (String profile : activeProfiles()) {
            if (isProductionProfile(profile)) {
                throw new SuperAdminBootstrapDisabledException(
                        "SUPER_ADMIN bootstrap is not permitted with the '" + profile
                                + "' profile active, regardless of app.environment ('" + current + "')");
            }
        }
    }

    /**
     * Creates the SUPER_ADMIN idempotently.
     *
     * @return the outcome and the resulting user id (or null when it already existed)
     */
    @Transactional
    public SuperAdminBootstrapDtos.BootstrapResult bootstrap(String rawEmail, String rawPassword) {
        assertEnvironmentAllowsBootstrap();
        String email = normalizer.normalizeEmail(rawEmail);
        if (rawPassword == null || rawPassword.isBlank()) {
            throw new BusinessException("A SUPER_ADMIN password is required");
        }

        Role role = requireSuperAdminRole();
        UUID platformOrgId = organizationRepository.findByCode(PLATFORM_ORG_CODE)
                .orElseThrow(() -> new BusinessException("The PLATFORM system organization is not seeded"))
                .getId();

        // Idempotency: the installation owner is the SUPER_ADMIN held by the PLATFORM system
        // organization. A SUPER_ADMIN in any business organization is not the owner and must never
        // satisfy this check (ADR-0032, ADR-0033).
        if (userRoleRepository.countActiveAssignmentsByRoleCodeInOrganization(SUPER_ADMIN_ROLE_CODE,
                platformOrgId) > 0) {
            return new SuperAdminBootstrapDtos.BootstrapResult("ALREADY_EXISTS", null);
        }

        User existing = userRepository.findByEmail(email).orElse(null);
        if (existing != null) {
            throw new BusinessException("A user with this email already exists and is not the SUPER_ADMIN");
        }

        passwordPolicy.validate(rawPassword, email);
        User superAdmin = new User();
        superAdmin.setOrganizationId(platformOrgId);
        superAdmin.setFirstName("Platform");
        superAdmin.setLastName("Administrator");
        superAdmin.setEmail(email);
        superAdmin.setMobile(null);
        superAdmin.setPassword(passwordEncoder.encode(rawPassword));
        superAdmin.setMustChangePassword(true);
        superAdmin.setStatus(Status.ACTIVE);
        superAdmin.setCreatedBy(IdentityConstants.SYSTEM_ACTOR_ID);
        superAdmin.setUpdatedBy(IdentityConstants.SYSTEM_ACTOR_ID);
        User saved = userRepository.save(superAdmin);

        UserRole assignment = new UserRole();
        assignment.setUserId(saved.getId());
        assignment.setRoleId(role.getId());
        assignment.setStatus(Status.ACTIVE);
        assignment.setCreatedBy(IdentityConstants.SYSTEM_ACTOR_ID);
        assignment.setUpdatedBy(IdentityConstants.SYSTEM_ACTOR_ID);
        userRoleRepository.save(assignment);

        securityEvents.audit(IdentityConstants.SYSTEM_ACTOR_ID, "SUPER_ADMIN_BOOTSTRAP", "user", saved.getId(),
                "Created SUPER_ADMIN " + email);
        return new SuperAdminBootstrapDtos.BootstrapResult("CREATED", saved.getId());
    }

    private Role requireSuperAdminRole() {
        return roleRepository.findByCodeAndOrganizationIdIsNull(SUPER_ADMIN_ROLE_CODE)
                .filter(role -> role.getStatus() == Status.ACTIVE)
                .orElseThrow(() -> new BusinessException("The SUPER_ADMIN role is not seeded or inactive"));
    }

    private List<String> activeProfiles() {
        String[] profiles = environment.getActiveProfiles();
        return profiles == null ? List.of() : List.of(profiles);
    }

    private boolean isProductionProfile(String profile) {
        return "prod".equalsIgnoreCase(profile) || "production".equalsIgnoreCase(profile);
    }
}
