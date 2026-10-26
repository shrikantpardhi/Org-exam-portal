package com.company.exam.identity.loadcohort;

import com.company.exam.common.audit.SecurityEventLogger;
import com.company.exam.common.enums.Status;
import com.company.exam.common.exception.BusinessException;
import com.company.exam.identity.IdentityConstants;
import com.company.exam.identity.entity.Organization;
import com.company.exam.identity.entity.Role;
import com.company.exam.identity.entity.User;
import com.company.exam.identity.entity.UserRole;
import com.company.exam.identity.repository.OrganizationRepository;
import com.company.exam.identity.repository.RoleRepository;
import com.company.exam.identity.repository.UserRepository;
import com.company.exam.identity.repository.UserRoleRepository;
import com.company.exam.identity.validation.IdentityNormalizer;
import com.company.exam.identity.validation.PasswordPolicy;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Provisions a synthetic load-test cohort with usable credentials
 * ({@code specs/10-P1-Identity-V1.5.md §56}, {@code specs/16-P7-Pilot-V1.5.md §24}; closes GAP-085).
 *
 * <p><b>Why this exists.</b> The production onboarding path deliberately cannot issue a usable
 * credential: {@code POST /api/v1/users} stores a random secret and the account is meant to be activated
 * through password reset (§56, §58). Reset delivery is not built (GAP-002), and a synthetic student has
 * no mailbox, so a 5,000-account cohort could never authenticate. GAP-085 recorded that.
 *
 * <p><b>What replaces the previous approach.</b> The load harness used to pipe raw {@code INSERT}
 * statements into PostgreSQL with a {@code {noop}}-prefixed plaintext password. That bypassed hashing,
 * wrote directly to tables the application owns, produced no audit trail, and was not environment-gated
 * at all. This service instead:
 *
 * <ul>
 *   <li>hashes every credential with the platform's own {@code PasswordEncoder} (no {@code {noop}});</li>
 *   <li>writes through the JPA entities and repositories, so the same invariants apply as in the API;</li>
 *   <li>records an audit entry per account through {@link SecurityEventLogger};</li>
 *   <li>is idempotent — an existing account is matched by email and re-issued rather than duplicated;</li>
 *   <li>is gated on both a property <em>and</em> {@code app.environment}, and refuses production.</li>
 * </ul>
 *
 * <p><b>No endpoint.</b> This class is a service, not a controller. It has no HTTP mapping, so nothing
 * here ships as a reachable API surface. It is invoked in-process by {@link LoadCohortCli}, which boots a
 * non-web context and exits. There is no production code path to it: with
 * {@code app.load-cohort.enabled} absent the bean is not created at all.
 *
 * <p>The gating is deliberately enforced in two places. {@code @ConditionalOnProperty} means the bean
 * does not exist unless an operator opts in; {@link #assertEnvironmentAllowsProvisioning()} means that
 * even when the bean exists, a production environment still fails closed. A single gate would make a
 * misconfiguration an incident rather than an inert setting.
 */
@Service
@ConditionalOnProperty(name = "app.load-cohort.enabled", havingValue = "true")
public class LoadCohortProvisioningService {

    private static final Logger log = LoggerFactory.getLogger(LoadCohortProvisioningService.class);

    /** The role every synthetic student receives ({@code specs/10-P1 §15}). */
    static final String STUDENT_ROLE_CODE = "STUDENT";

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final RoleRepository roleRepository;
    private final OrganizationRepository organizationRepository;
    private final IdentityNormalizer normalizer;
    private final PasswordPolicy passwordPolicy;
    private final SecurityEventLogger securityEvents;
    private final LoadCohortProperties properties;
    private final Environment environment;
    private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;
    private final SecureRandom random = new SecureRandom();

    public LoadCohortProvisioningService(UserRepository userRepository,
            UserRoleRepository userRoleRepository,
            RoleRepository roleRepository,
            OrganizationRepository organizationRepository,
            IdentityNormalizer normalizer,
            PasswordPolicy passwordPolicy,
            SecurityEventLogger securityEvents,
            LoadCohortProperties properties,
            Environment environment,
            org.springframework.security.crypto.password.PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
        this.roleRepository = roleRepository;
        this.organizationRepository = organizationRepository;
        this.normalizer = normalizer;
        this.passwordPolicy = passwordPolicy;
        this.securityEvents = securityEvents;
        this.properties = properties;
        this.environment = environment;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * The environment reported by configuration ({@code app.environment}).
     *
     * <p>Read from the live {@link Environment} rather than injected as a value so a test can prove the
     * gate against a production value without rebuilding the context.
     */
    public String currentEnvironment() {
        return environment.getProperty("app.environment", "unknown");
    }

    /**
     * Fails closed unless the current environment permits synthetic provisioning.
     *
     * <p>Two independent signals are checked, because either alone is defeatable:
     *
     * <ol>
     *   <li>{@code app.environment} — the platform's declared environment; and</li>
     *   <li>the <b>active Spring profiles</b> — the profile the process was actually started with.</li>
     * </ol>
     *
     * <p>The second check exists because the first is not sufficient in practice. Spring resolves
     * {@code app.environment} with normal property precedence, so an {@code APP_ENVIRONMENT} environment
     * variable (which {@code docker-compose.yml} sets) overrides the value in a profile-specific YAML file.
     * Starting the process with {@code --spring.profiles.active=prod} inside a container whose environment
     * still says {@code staging} therefore leaves {@code app.environment=staging} while the process is
     * genuinely running the production profile. Checking the active profile closes that hole.
     *
     * @throws LoadCohortDisabledException when {@code app.environment} or an active profile forbids it
     */
    public void assertEnvironmentAllowsProvisioning() {
        String current = currentEnvironment();
        String forbidden = properties.getForbiddenEnvironment();
        if (current.equalsIgnoreCase(forbidden)) {
            // Deliberately not a silent no-op: an attempt to provision in production is an incident.
            throw new LoadCohortDisabledException(
                    "Synthetic load-cohort provisioning is not permitted in the '" + current
                            + "' environment; it would create accounts with known credentials");
        }

        for (String profile : activeProfiles()) {
            if (isProductionProfile(profile)) {
                throw new LoadCohortDisabledException(
                        "Synthetic load-cohort provisioning is not permitted with the '" + profile
                                + "' profile active, regardless of app.environment ('" + current + "')");
            }
        }
    }

    /** The Spring profiles this process is actually running with. */
    private List<String> activeProfiles() {
        String[] profiles = environment.getActiveProfiles();
        return profiles == null ? List.of() : List.of(profiles);
    }

    /** Whether a profile name denotes production. Accepts both the short and long spellings. */
    private boolean isProductionProfile(String profile) {
        return "prod".equalsIgnoreCase(profile) || "production".equalsIgnoreCase(profile);
    }

    /**
     * Provisions the cohort idempotently.
     *
     * <p>Each member is matched by normalised email. A member that does not exist is created with the
     * STUDENT role; a member that exists keeps its identity and has its credential re-issued and its role
     * re-asserted. Either way exactly one account exists per email afterwards, so re-running the seed does
     * not duplicate users.
     *
     * @param members the accounts to provision
     * @return the per-account outcome, including the generated credential
     * @throws LoadCohortDisabledException when the environment forbids provisioning
     * @throws BusinessException           when the request violates a bound or an invariant
     */
    @Transactional
    public LoadCohortDtos.ProvisioningResult provision(List<LoadCohortDtos.CohortMember> members) {
        assertEnvironmentAllowsProvisioning();

        if (members == null || members.isEmpty()) {
            return new LoadCohortDtos.ProvisioningResult(0, 0, 0, List.of());
        }
        if (members.size() > properties.getMaxCohortSize()) {
            throw new BusinessException("Cohort of " + members.size() + " exceeds the configured maximum of "
                    + properties.getMaxCohortSize());
        }

        Organization organization = requireActiveOrganization();
        Role studentRole = requireStudentRole();

        List<LoadCohortDtos.MemberOutcome> outcomes = new ArrayList<>(members.size());
        int created = 0;
        int updated = 0;
        int skipped = 0;

        for (LoadCohortDtos.CohortMember member : members) {
            String email = normalizer.normalizeEmail(member.email());
            requireSyntheticEmail(email);

            // Generate and validate the credential before touching persistence, so an unusable secret can
            // never reach the database.
            String credential = generateCredential();
            passwordPolicy.validate(credential, email);
            String hash = passwordEncoder.encode(credential);

            Optional<User> existing = userRepository.findByEmail(email);
            if (existing.isPresent()) {
                User user = existing.get();
                if (isProvisionedLoadAccount(user)) {
                    user.setPassword(hash);
                    user.setStatus(Status.ACTIVE);
                    userRepository.save(user);
                    ensureRole(user.getId(), studentRole.getId());
                    securityEvents.audit(IdentityConstants.SYSTEM_ACTOR_ID, "LOAD_COHORT_REISSUE",
                            "user", user.getId(), "Re-issued synthetic load credential for " + email);
                    outcomes.add(new LoadCohortDtos.MemberOutcome(email, user.getId(), credential, "UPDATED"));
                    updated++;
                } else {
                    // Never silently take over an account that is not ours: that would let a load run
                    // reset an arbitrary user's password.
                    securityEvents.audit(IdentityConstants.SYSTEM_ACTOR_ID, "LOAD_COHORT_CONFLICT",
                            "user", user.getId(), "Refused to re-issue credential for non-load account " + email);
                    outcomes.add(new LoadCohortDtos.MemberOutcome(email, user.getId(), null, "SKIPPED"));
                    skipped++;
                }
                continue;
            }

            User user = new User();
            user.setOrganizationId(organization.getId());
            user.setFirstName(member.firstName() == null ? "Load" : member.firstName().trim());
            user.setLastName(member.lastName() == null ? "Student" : member.lastName().trim());
            user.setEmail(email);
            user.setMobile(null);
            user.setPassword(hash);
            user.setStatus(Status.ACTIVE);
            // The CLI runs without an authenticated principal, so the JPA auditor would leave these null.
            // Attribute the row to the well-known system actor instead, matching how other background
            // operations are recorded (§31).
            user.setCreatedBy(IdentityConstants.SYSTEM_ACTOR_ID);
            user.setUpdatedBy(IdentityConstants.SYSTEM_ACTOR_ID);
            User saved = userRepository.save(user);
            ensureRole(saved.getId(), studentRole.getId());
            securityEvents.audit(IdentityConstants.SYSTEM_ACTOR_ID, "LOAD_COHORT_CREATE",
                    "user", saved.getId(), "Created synthetic load account " + email);
            outcomes.add(new LoadCohortDtos.MemberOutcome(email, saved.getId(), credential, "CREATED"));
            created++;
        }

        log.info("Load-cohort provisioning: created={} updated={} skipped={}", created, updated, skipped);
        return new LoadCohortDtos.ProvisioningResult(created, updated, skipped, outcomes);
    }

    /**
     * A load account is one whose email is the synthetic {@code .test} domain.
     *
     * <p>The check is what stops a mistyped or malicious argument from resetting a real account: only
     * addresses that could not belong to a person are eligible for credential re-issue.
     */
    private boolean isProvisionedLoadAccount(User user) {
        String email = user.getEmail();
        return email != null && email.toLowerCase(java.util.Locale.ROOT).endsWith(".test");
    }

    private void requireSyntheticEmail(String email) {
        if (email == null || !email.toLowerCase(java.util.Locale.ROOT).endsWith(".test")) {
            throw new BusinessException(
                    "Load-cohort provisioning only accepts synthetic '.test' addresses; refused: " + email);
        }
    }

    private void ensureRole(java.util.UUID userId, java.util.UUID roleId) {
        Optional<UserRole> current = userRoleRepository.findByUserIdAndRoleId(userId, roleId);
        if (current.isPresent()) {
            UserRole userRole = current.get();
            if (userRole.getStatus() != Status.ACTIVE) {
                userRole.setStatus(Status.ACTIVE);
                userRoleRepository.save(userRole);
            }
            return;
        }
        UserRole userRole = new UserRole();
        userRole.setUserId(userId);
        userRole.setRoleId(roleId);
        userRole.setStatus(Status.ACTIVE);
        userRole.setCreatedBy(IdentityConstants.SYSTEM_ACTOR_ID);
        userRole.setUpdatedBy(IdentityConstants.SYSTEM_ACTOR_ID);
        userRoleRepository.save(userRole);
    }

    private Role requireStudentRole() {
        return roleRepository.findByCodeAndOrganizationIdIsNull(STUDENT_ROLE_CODE)
                .filter(role -> role.getStatus() == Status.ACTIVE)
                .orElseThrow(() -> new BusinessException("The STUDENT role is not configured or is inactive"));
    }

    private Organization requireActiveOrganization() {
        return organizationRepository.findByStatus(Status.ACTIVE).stream().findFirst()
                .orElseThrow(() -> new BusinessException("No active organization is configured"));
    }

    /**
     * Generates a credential that satisfies {@link PasswordPolicy} and contains no user-specific data.
     *
     * <p>32 random bytes rendered as URL-safe base64 gives 43 characters, comfortably above the policy
     * minimum, and is unguessable. The value is returned once to the caller and never logged or stored.
     */
    private String generateCredential() {
        byte[] buffer = new byte[32];
        random.nextBytes(buffer);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buffer);
    }
}
