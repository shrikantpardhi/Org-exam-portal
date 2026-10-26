package com.company.exam.identity.service;

import com.company.exam.administration.api.BusinessRuleResolver;
import com.company.exam.common.api.ApiError;
import com.company.exam.common.enums.Status;
import com.company.exam.common.exception.AuthorizationException;
import com.company.exam.common.exception.BusinessException;
import com.company.exam.common.exception.ConflictException;
import com.company.exam.common.exception.NotFoundException;
import com.company.exam.common.exception.ValidationException;
import com.company.exam.identity.IdentityConstants;
import com.company.exam.identity.dto.CurrentUserDtos;
import com.company.exam.identity.dto.UserDtos;
import com.company.exam.identity.entity.Organization;
import com.company.exam.identity.entity.Role;
import com.company.exam.identity.entity.User;
import com.company.exam.identity.entity.UserRole;
import com.company.exam.identity.exception.AdminCapExceededException;
import com.company.exam.identity.exception.LastAdminException;
import com.company.exam.identity.exception.SelfRoleModificationException;
import com.company.exam.identity.exception.SuperAdminImmutabilityException;
import com.company.exam.identity.mapper.IdentityMapper;
import com.company.exam.identity.repository.OrganizationRepository;
import com.company.exam.identity.repository.RoleRepository;
import com.company.exam.identity.repository.UserRepository;
import com.company.exam.identity.repository.UserRoleRepository;
import com.company.exam.common.audit.SecurityEventLogger;
import com.company.exam.identity.validation.IdentityNormalizer;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * User management workflows ({@code specs/10-P1-Identity-V1.5.md §53-§59, §82}).
 *
 * <p>It creates, reads, updates, and archives users and never issues tokens. Duplicate prevention
 * is pre-validated and backed by the database unique constraints; races become 409 rather than an
 * internal error. Role changes enforce the privileged role change policy ({@code §63}).
 */
@Service
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final OrganizationRepository organizationRepository;
    private final PasswordEncoder passwordEncoder;
    private final IdentityNormalizer normalizer;
    private final IdentityMapper mapper;
    private final AuthorizationService authorizationService;
    private final RolePermissionPolicy permissionPolicy;
    private final SessionService sessionService;
    private final PasswordResetService passwordResetService;
    private final BusinessRuleResolver ruleResolver;
    private final SecurityEventLogger securityEvents;

    private static final String MAX_ADMINS_RULE = "MAX_ADMINS_PER_ORG";
    private static final long DEFAULT_MAX_ADMINS = 1;

    public UserService(UserRepository userRepository, RoleRepository roleRepository,
            UserRoleRepository userRoleRepository, OrganizationRepository organizationRepository,
            PasswordEncoder passwordEncoder, IdentityNormalizer normalizer, IdentityMapper mapper,
            AuthorizationService authorizationService, SessionService sessionService,
            PasswordResetService passwordResetService, BusinessRuleResolver ruleResolver,
            SecurityEventLogger securityEvents, RolePermissionPolicy permissionPolicy) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.userRoleRepository = userRoleRepository;
        this.organizationRepository = organizationRepository;
        this.passwordEncoder = passwordEncoder;
        this.normalizer = normalizer;
        this.mapper = mapper;
        this.authorizationService = authorizationService;
        this.sessionService = sessionService;
        this.passwordResetService = passwordResetService;
        this.ruleResolver = ruleResolver;
        this.securityEvents = securityEvents;
        this.permissionPolicy = permissionPolicy;
    }

    @Transactional
    public UserDtos.UserDetail create(UserDtos.CreateUserRequest request, UUID actorId) {
        String email = normalizer.normalizeEmail(request.email());
        String mobile = normalizer.normalizeMobile(request.mobile());
        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("A user with this email already exists");
        }
        if (mobile != null && userRepository.existsByMobile(mobile)) {
            throw new ConflictException("A user with this mobile already exists");
        }
        Organization organization = activeOrganization();
        List<Role> roles = requireActiveRoles(request.roles());
        // D-08 Rule A (ADR-0044) also covers creation. Guarding only replaceRoles would leave the
        // original escalation reachable through POST /users, which is the path a caller with
        // USER_CREATE would actually use.
        if (actorId != null) {
            permissionPolicy.checkAssignable(roles.stream().map(Role::getId).toList(), actorId);
        }

        User user = new User();
        user.setOrganizationId(organization.getId());
        user.setFirstName(request.firstName().trim());
        user.setLastName(request.lastName().trim());
        user.setEmail(email);
        user.setMobile(mobile);
        // Onboarding policy (§56, §58): the API never accepts a password. The account starts with a
        // non-usable credential and is activated through password reset.
        user.setPassword(passwordEncoder.encode(randomSecret()));
        // First password is delivered as a reset link; the owner must choose it before anything
        // else is reachable (ADR-0034).
        user.setMustChangePassword(true);
        user.setStatus(Status.ACTIVE);

        User saved;
        try {
            saved = userRepository.save(user);
            for (Role role : roles) {
                assignRole(saved.getId(), role.getId());
            }
        } catch (DataIntegrityViolationException race) {
            throw new ConflictException("A user with this email or mobile already exists");
        }
        passwordResetService.deliverFirstPasswordInvitation(saved);
        securityEvents.roleChanged(actorId, saved.getId(), "USER_CREATE", "Created user " + saved.getEmail());
        return mapper.toDetail(saved, roles.stream().map(Role::getCode).toList());
    }

    /**
     * A user's detail, scoped to the caller's organization (D-10, R2).
     *
     * <p>A user in another organization returns 404, not 403: 403 would confirm that the id exists,
     * which is the disclosure rule {@code 10-P1} §25 already applies to password reset.
     */
    @Transactional(readOnly = true)
    public UserDtos.UserDetail get(UUID id, UUID actorId) {
        User user = requireUserInActorScope(id, actorId);
        return mapper.toDetail(user, roleRepository.findEffectiveRoleCodes(id));
    }

    /** The authenticated user's safe profile context ({@code specs/10-P1-Identity-V1.5.md §32, §82}). */
    @Transactional(readOnly = true)
    public CurrentUserDtos.CurrentUserResponse currentUser(UUID userId) {
        User user = requireUser(userId);
        return new CurrentUserDtos.CurrentUserResponse(
                user.getId(), user.getFirstName(), user.getLastName(), user.getEmail(),
                roleRepository.findEffectiveRoleCodes(userId),
                authorizationService.effectivePermissions(userId),
                user.isMustChangePassword());
    }

    /**
     * The caller's organization's users (D-10, R2).
     *
     * <p>Before this, a {@code DEFAULT}-organization ADMIN received all 5,010 users of a
     * two-organization deployment, including the installation owner. The scope is derived from the
     * actor, never from a request parameter: a client-supplied organization id would be an
     * authorization decision taken by the caller.
     */
    @Transactional(readOnly = true)
    public Page<UserDtos.UserSummary> list(Status status, UUID roleId, String search, Pageable pageable,
            UUID actorId) {
        Page<User> page = userRepository.findAll(
                userSpecification(status, roleId, search, organizationOf(actorId)), pageable);
        Map<UUID, List<String>> rolesByUser = roleCodesForUsers(page.getContent());
        return page.map(user -> mapper.toSummary(user, rolesByUser.getOrDefault(user.getId(), List.of())));
    }

    @Transactional
    public UserDtos.UserDetail update(UUID id, UserDtos.UpdateUserRequest request, UUID actorId) {
        User user = requireUser(id);
        if (request.firstName() != null) {
            user.setFirstName(request.firstName().trim());
        }
        if (request.lastName() != null) {
            user.setLastName(request.lastName().trim());
        }
        if (request.mobile() != null) {
            String mobile = normalizer.normalizeMobile(request.mobile());
            if (mobile != null && !mobile.equals(user.getMobile()) && userRepository.existsByMobile(mobile)) {
                throw new ConflictException("A user with this mobile already exists");
            }
            user.setMobile(mobile);
        }
        if (request.roles() != null) {
            replaceRoles(user, request.roles(), actorId);
        }
        User saved = userRepository.save(user);
        return mapper.toDetail(saved, roleRepository.findEffectiveRoleCodes(id));
    }

    @Transactional
    public void archive(UUID id, UUID actorId) {
        User user = requireUser(id);
        guardSuperAdminImmutability(id);
        guardLastAdminRemoval(id);
        user.setStatus(Status.ARCHIVED);
        userRepository.save(user);
        sessionService.expireAllForUser(id);
        authorizationService.evictAfterCommit(id);
        securityEvents.roleChanged(actorId, id, "USER_ARCHIVE", "Archived user " + user.getEmail());
    }

    /**
     * The acting user's organization id, or {@code null} when it cannot be resolved.
     *
     * <p>Callers treat {@code null} as "see nothing" (fail closed), never as "see everything".
     */
    private UUID organizationOf(UUID actorId) {
        if (actorId == null) {
            return null;
        }
        return userRepository.findById(actorId).map(User::getOrganizationId).orElse(null);
    }

    /** Loads a user, refusing one outside the actor's organization with 404 (D-10). */
    private User requireUserInActorScope(UUID id, UUID actorId) {
        User user = requireUser(id);
        UUID organizationId = organizationOf(actorId);
        if (organizationId == null || !organizationId.equals(user.getOrganizationId())) {
            throw NotFoundException.of("User", id);
        }
        return user;
    }

    @Transactional(readOnly = true)
    public User requireUser(UUID id) {
        return userRepository.findById(id).orElseThrow(() -> NotFoundException.of("User", id));
    }

    private void assignRole(UUID userId, UUID roleId) {
        guardAdminCap(userId, roleId);
        UserRole userRole = new UserRole();
        userRole.setUserId(userId);
        userRole.setRoleId(roleId);
        userRole.setStatus(Status.ACTIVE);
        userRoleRepository.save(userRole);
    }

    private void replaceRoles(User user, List<UUID> desiredRoleIds, UUID actorId) {
        guardSuperAdminImmutability(user.getId());
        if (actorId != null && actorId.equals(user.getId())) {
            throw new SelfRoleModificationException();
        }
        List<Role> desiredRoles = requireActiveRoles(desiredRoleIds);
        // D-08 Rule A (ADR-0044): a caller may not assign a role carrying a permission it does not
        // hold. Checked on the desired set, so "assign this role" cannot become a quieter way to
        // grant authority than naming the permission directly. Runs after the self-guard above so
        // SELF_PERMISSION_MODIFICATION keeps precedence.
        if (actorId != null) {
            permissionPolicy.checkAssignable(desiredRoles.stream().map(Role::getId).toList(), actorId);
        }
        Set<UUID> desired = desiredRoles.stream().map(Role::getId).collect(Collectors.toSet());

        List<UserRole> current = userRoleRepository.findByUserIdAndStatus(user.getId(), Status.ACTIVE);
        Set<UUID> currentIds = current.stream().map(UserRole::getRoleId).collect(Collectors.toSet());
        Optional<UUID> adminRoleId = roleRepository.findByCodeAndOrganizationIdIsNull(IdentityConstants.ADMIN_ROLE_CODE).map(Role::getId);

        boolean grantingAdmin = adminRoleId.isPresent() && desired.contains(adminRoleId.get())
                && !currentIds.contains(adminRoleId.get());
        boolean revokingAdmin = adminRoleId.isPresent() && !desired.contains(adminRoleId.get())
                && currentIds.contains(adminRoleId.get());
        if (grantingAdmin || revokingAdmin) {
            if (actorId == null || !authorizationService.hasPermission(actorId, IdentityConstants.ADMIN_ROLE_ASSIGN)) {
                throw new AuthorizationException("Admin role assignment requires " + IdentityConstants.ADMIN_ROLE_ASSIGN);
            }
        }
        if (grantingAdmin && userRoleRepository.countActiveAssignmentsByRoleCodeInOrganization(
                IdentityConstants.ADMIN_ROLE_CODE, user.getOrganizationId()) >= maxAdminsPerOrg()) {
            throw new AdminCapExceededException();
        }
        if (revokingAdmin && userRoleRepository.countActiveAssignmentsByRoleCode(IdentityConstants.ADMIN_ROLE_CODE) <= 1) {
            throw new LastAdminException();
        }

        Map<UUID, String> codes = roleCodeMap(currentIds, desired);

        for (UserRole userRole : current) {
            if (!desired.contains(userRole.getRoleId())) {
                userRole.setStatus(Status.INACTIVE);
                userRoleRepository.save(userRole);
                securityEvents.roleChanged(actorId, user.getId(), "ROLE_REVOKE",
                        "Removed role " + codes.getOrDefault(userRole.getRoleId(), userRole.getRoleId().toString()));
            }
        }
        for (UUID roleId : desired) {
            if (currentIds.contains(roleId)) {
                continue;
            }
            UserRole userRole = userRoleRepository.findByUserIdAndRoleId(user.getId(), roleId).orElseGet(() -> {
                UserRole created = new UserRole();
                created.setUserId(user.getId());
                created.setRoleId(roleId);
                return created;
            });
            userRole.setStatus(Status.ACTIVE);
            userRoleRepository.save(userRole);
            securityEvents.roleChanged(actorId, user.getId(), "ROLE_ASSIGN",
                    "Added role " + codes.getOrDefault(roleId, roleId.toString()));
        }
        authorizationService.evictAfterCommit(user.getId());
    }

    private void guardLastAdminRemoval(UUID userId) {
        Optional<UUID> adminRoleId = roleRepository.findByCodeAndOrganizationIdIsNull(IdentityConstants.ADMIN_ROLE_CODE).map(Role::getId);
        if (adminRoleId.isEmpty()) {
            return;
        }
        boolean userIsActiveAdmin = userRoleRepository.findByUserIdAndRoleId(userId, adminRoleId.get())
                .filter(userRole -> userRole.getStatus() == Status.ACTIVE)
                .isPresent();
        if (userIsActiveAdmin
                && userRoleRepository.countActiveAssignmentsByRoleCode(IdentityConstants.ADMIN_ROLE_CODE) <= 1) {
            throw new LastAdminException();
        }
    }

    private void guardAdminCap(UUID userId, UUID roleId) {
        Optional<UUID> adminRoleId = roleRepository.findByCodeAndOrganizationIdIsNull(IdentityConstants.ADMIN_ROLE_CODE).map(Role::getId);
        if (adminRoleId.isEmpty() || !adminRoleId.get().equals(roleId)) {
            return;
        }
        User user = requireUser(userId);
        if (userRoleRepository.countActiveAssignmentsByRoleCodeInOrganization(
                IdentityConstants.ADMIN_ROLE_CODE, user.getOrganizationId()) >= maxAdminsPerOrg()) {
            throw new AdminCapExceededException();
        }
    }

    private void guardSuperAdminImmutability(UUID userId) {
        if (isSuperAdmin(userId)) {
            throw new SuperAdminImmutabilityException();
        }
    }

    private boolean isSuperAdmin(UUID userId) {
        return roleRepository.findByCodeAndOrganizationIdIsNull(IdentityConstants.SUPER_ADMIN_ROLE_CODE)
                .map(role -> userRoleRepository.findByUserIdAndRoleId(userId, role.getId())
                        .filter(userRole -> userRole.getStatus() == Status.ACTIVE)
                        .isPresent())
                .orElse(false);
    }

    private long maxAdminsPerOrg() {
        return ruleResolver.resolve(MAX_ADMINS_RULE)
                .flatMap(rule -> rule.decimal("maxAdmins"))
                .map(java.math.BigDecimal::longValue)
                .filter(value -> value >= 0)
                .orElse(DEFAULT_MAX_ADMINS);
    }

    private Map<UUID, String> roleCodeMap(Set<UUID> currentIds, Set<UUID> desired) {
        Set<UUID> all = new HashSet<>(currentIds);
        all.addAll(desired);
        if (all.isEmpty()) {
            return Map.of();
        }
        return roleRepository.findAllByIdIn(all).stream().collect(Collectors.toMap(Role::getId, Role::getCode));
    }

    private List<Role> requireActiveRoles(List<UUID> roleIds) {
        List<UUID> distinct = roleIds.stream().distinct().toList();
        if (distinct.isEmpty()) {
            return List.of();
        }
        List<Role> roles = roleRepository.findAllByIdIn(distinct);
        if (roles.size() != distinct.size() || roles.stream().anyMatch(role -> role.getStatus() != Status.ACTIVE)) {
            throw new ValidationException("Validation failed",
                    List.of(ApiError.of("roles", "INVALID_ROLE", "One or more roles are invalid or inactive")));
        }
        return roles;
    }

    private Organization activeOrganization() {
        return organizationRepository.findByStatus(Status.ACTIVE).stream().findFirst()
                .orElseThrow(() -> new BusinessException("No active organization is configured"));
    }

    private Map<UUID, List<String>> roleCodesForUsers(List<User> users) {
        if (users.isEmpty()) {
            return Map.of();
        }
        List<UUID> userIds = users.stream().map(User::getId).toList();
        List<UserRole> assignments = userRoleRepository.findByUserIdInAndStatus(userIds, Status.ACTIVE);
        if (assignments.isEmpty()) {
            return Map.of();
        }
        Map<UUID, String> roleCodes = roleRepository
                .findAllByIdIn(assignments.stream().map(UserRole::getRoleId).collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(Role::getId, Role::getCode));
        return assignments.stream().collect(Collectors.groupingBy(UserRole::getUserId,
                Collectors.mapping(userRole -> roleCodes.getOrDefault(userRole.getRoleId(), userRole.getRoleId().toString()),
                        Collectors.toList())));
    }

    private Specification<User> userSpecification(Status status, UUID roleId, String search, UUID organizationId) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            // Organization scope first: it is the one predicate that must never be optional.
            if (organizationId != null) {
                predicates.add(cb.equal(root.get("organizationId"), organizationId));
            } else {
                // Fail closed (G-10): an actor with no resolvable organization sees nothing rather
                // than everything. A null here means the caller could not be attributed, which is a
                // refusal case, not a licence to read the installation.
                predicates.add(cb.disjunction());
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (search != null && !search.isBlank()) {
                String like = "%" + search.toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("email")), like),
                        cb.like(cb.lower(root.get("firstName")), like),
                        cb.like(cb.lower(root.get("lastName")), like)));
            }
            if (roleId != null) {
                Subquery<UUID> subquery = query.subquery(UUID.class);
                Root<UserRole> userRole = subquery.from(UserRole.class);
                subquery.select(userRole.get("userId")).where(
                        cb.equal(userRole.get("roleId"), roleId),
                        cb.equal(userRole.get("status"), Status.ACTIVE),
                        cb.equal(userRole.get("userId"), root.get("id")));
                predicates.add(cb.exists(subquery));
            }
            return predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private String randomSecret() {
        byte[] buffer = new byte[32];
        new SecureRandom().nextBytes(buffer);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buffer);
    }
}
