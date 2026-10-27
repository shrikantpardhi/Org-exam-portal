package com.company.exam.platform.service;

import com.company.exam.administration.api.BusinessRuleResolver;
import com.company.exam.common.audit.SecurityEventLogger;
import com.company.exam.common.api.PageResponse;
import com.company.exam.common.enums.Status;
import com.company.exam.common.exception.AuthorizationException;
import com.company.exam.common.exception.ConflictException;
import com.company.exam.common.exception.ValidationException;
import com.company.exam.common.exception.NotFoundException;
import com.company.exam.identity.entity.Organization;
import com.company.exam.identity.entity.Role;
import com.company.exam.identity.entity.User;
import com.company.exam.identity.entity.UserRole;
import com.company.exam.identity.repository.OrganizationRepository;
import com.company.exam.identity.repository.RoleRepository;
import com.company.exam.identity.repository.UserRepository;
import com.company.exam.identity.repository.UserRoleRepository;
import com.company.exam.identity.service.AuthorizationService;
import com.company.exam.identity.service.PasswordResetService;
import com.company.exam.identity.service.RolePermissionPolicy;
import com.company.exam.identity.service.SessionService;
import com.company.exam.identity.validation.IdentityNormalizer;
import com.company.exam.platform.dto.PlatformDtos;
import jakarta.persistence.criteria.Predicate;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
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
 * SUPER_ADMIN business-organization lifecycle ({@code specs/05 §72.2-§72.5}; ADR-0032, ADR-0033).
 *
 * <p>The installation-owner can create business organizations and assign each one a single ADMIN.
 * {@code PLATFORM} is the system organization: it is never listed, never returned by detail, and
 * never counts toward business totals. The one-ADMIN-per-org cap is data-driven through
 * {@code MAX_ADMINS_PER_ORG} (ADR-0032).
 */
@Service
public class PlatformOrganizationService {

    static final String PLATFORM_CODE = "PLATFORM";
    static final String ADMIN_ROLE_CODE = "ADMIN";
    static final String MAX_ADMINS_RULE = "MAX_ADMINS_PER_ORG";
    static final long DEFAULT_MAX_ADMINS = 1;
    static final String PRIMARY_ASSIGN_PERMISSION = "PLATFORM_ADMIN_PRIMARY_ASSIGN";

    private final OrganizationRepository organizationRepository;
    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final PasswordEncoder passwordEncoder;
    private final IdentityNormalizer normalizer;
    private final PasswordResetService passwordResetService;
    private final BusinessRuleResolver ruleResolver;
    private final SecurityEventLogger securityEvents;
    private final AuthorizationService authorizationService;
    private final SessionService sessionService;
    private final RolePermissionPolicy permissionPolicy;

    public PlatformOrganizationService(OrganizationRepository organizationRepository,
            RoleRepository roleRepository, UserRepository userRepository, UserRoleRepository userRoleRepository,
            PasswordEncoder passwordEncoder, IdentityNormalizer normalizer,
            PasswordResetService passwordResetService, BusinessRuleResolver ruleResolver,
            SecurityEventLogger securityEvents, AuthorizationService authorizationService,
            SessionService sessionService, RolePermissionPolicy permissionPolicy) {
        this.organizationRepository = organizationRepository;
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
        this.passwordEncoder = passwordEncoder;
        this.normalizer = normalizer;
        this.passwordResetService = passwordResetService;
        this.ruleResolver = ruleResolver;
        this.securityEvents = securityEvents;
        this.authorizationService = authorizationService;
        this.sessionService = sessionService;
        this.permissionPolicy = permissionPolicy;
    }

    @Transactional
    public PlatformDtos.OrganizationSummary createOrganization(PlatformDtos.CreateOrganizationRequest request,
            UUID actorId) {
        String code = normalizeCode(request.code());
        if (PLATFORM_CODE.equals(code)) {
            throw new ConflictException("PLATFORM is a reserved system organization code");
        }
        if (organizationRepository.findByCode(code).isPresent()) {
            throw new ConflictException("An organization with this code already exists");
        }
        Organization organization = new Organization();
        organization.setCode(code);
        organization.setName(request.name().trim());
        organization.setDescription(blankToNull(request.description()));
        // Profile (ADR-0051, collected at creation since ADR-0056). blankToNull keeps "" and null
        // indistinguishable on the way in, so "the operator left it empty" and "the operator never
        // saw the field" cannot produce two different stored values.
        organization.setAddressLine1(blankToNull(request.addressLine1()));
        organization.setAddressLine2(blankToNull(request.addressLine2()));
        organization.setCity(blankToNull(request.city()));
        organization.setStateRegion(blankToNull(request.stateRegion()));
        organization.setPostalCode(blankToNull(request.postalCode()));
        organization.setCountry(blankToNull(request.country()));
        organization.setContactEmail(blankToNull(request.contactEmail()));
        organization.setContactPhone(blankToNull(request.contactPhone()));
        organization.setWebsiteUrl(blankToNull(request.websiteUrl()));
        organization.setLogoUrl(blankToNull(request.logoUrl()));
        organization.setStatus(Status.ACTIVE);
        Organization saved = organizationRepository.save(organization);
        securityEvents.audit(actorId, "ORGANIZATION_CREATE", "Organization", saved.getId(),
                "Created business organization " + saved.getCode());
        return new PlatformDtos.OrganizationSummary(saved.getId(), saved.getCode(), saved.getName(),
                saved.getStatus());
    }

    @Transactional(readOnly = true)
    public PageResponse<PlatformDtos.OrganizationSummary> listOrganizations(Status status, String search,
            Pageable pageable) {
        String term = search == null ? "" : search.trim();
        Specification<Organization> specification = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.notEqual(root.get("code"), PLATFORM_CODE));
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (!term.isEmpty()) {
                String like = "%" + term.toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("code")), like),
                        cb.like(cb.lower(root.get("name")), like)));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        Page<Organization> page = organizationRepository.findAll(specification, pageable);
        return PageResponse.from(page,
                org -> new PlatformDtos.OrganizationSummary(org.getId(), org.getCode(), org.getName(),
                        org.getStatus()));
    }

    /**
     * Corrects a business organization's profile (ADR-0056, §72.17).
     *
     * <p>Deliberately cannot change {@code code}, {@code status} or the primary administrator: each of
     * those already has its own endpoint with its own permission and its own audit action, and
     * folding them here would mean one call that silently does three unrelated things.
     *
     * <p>{@code name} is the only field that is refused when blank, because the column is
     * {@code NOT NULL}. Every profile column is nullable, so blank clears it — which is how an
     * operator removes a stale phone number.
     */
    @Transactional
    public PlatformDtos.OrganizationDetail updateOrganization(UUID organizationId,
            PlatformDtos.UpdateOrganizationRequest request, UUID actorId) {
        Organization organization = requireBusinessOrganization(organizationId);
        if (request.name() != null) {
            String name = request.name().trim();
            if (name.isEmpty()) {
                throw new ValidationException("Organization name cannot be blank");
            }
            organization.setName(name);
        }
        if (request.description() != null) {
            organization.setDescription(blankToNull(request.description()));
        }
        if (request.addressLine1() != null) {
            organization.setAddressLine1(blankToNull(request.addressLine1()));
        }
        if (request.addressLine2() != null) {
            organization.setAddressLine2(blankToNull(request.addressLine2()));
        }
        if (request.city() != null) {
            organization.setCity(blankToNull(request.city()));
        }
        if (request.stateRegion() != null) {
            organization.setStateRegion(blankToNull(request.stateRegion()));
        }
        if (request.postalCode() != null) {
            organization.setPostalCode(blankToNull(request.postalCode()));
        }
        if (request.country() != null) {
            organization.setCountry(blankToNull(request.country()));
        }
        if (request.contactEmail() != null) {
            organization.setContactEmail(blankToNull(request.contactEmail()));
        }
        if (request.contactPhone() != null) {
            organization.setContactPhone(blankToNull(request.contactPhone()));
        }
        if (request.websiteUrl() != null) {
            organization.setWebsiteUrl(blankToNull(request.websiteUrl()));
        }
        if (request.logoUrl() != null) {
            organization.setLogoUrl(blankToNull(request.logoUrl()));
        }
        organizationRepository.save(organization);
        securityEvents.audit(actorId, "ORGANIZATION_UPDATE", "Organization", organizationId,
                "Corrected the profile of business organization " + organization.getCode());
        return getOrganization(organizationId);
    }

    /**
     * Changes a business organization's status (ADR-0055, §72.16).
     *
     * <p>The status is not cosmetic: {@code AuthenticationService} refuses a login whose organization
     * is not {@code ACTIVE}, so {@code INACTIVE} suspends the whole tenant. That is the intended
     * meaning of the lever, and the reason it is refused for {@code PLATFORM} — deactivating the
     * installation itself would lock out every operator including the one making the change.
     *
     * <p>Only {@code ACTIVE} and {@code INACTIVE} are accepted (the DTO carries the same list): a third
     * state would not change behaviour, because the login gate collapses every non-{@code ACTIVE} value.
     */
    @Transactional
    public PlatformDtos.OrganizationDetail changeStatus(UUID organizationId, Status status, UUID actorId) {
        Organization organization = requireBusinessOrganization(organizationId);
        if (status != Status.ACTIVE && status != Status.INACTIVE) {
            throw new ValidationException("Organization status must be ACTIVE or INACTIVE");
        }
        Status previous = organization.getStatus();
        if (previous == status) {
            // Idempotent: re-applying the current state is what a retry looks like, and reporting a
            // change that did not happen would put a false entry in the audit trail.
            return getOrganization(organizationId);
        }
        organization.setStatus(status);
        organizationRepository.save(organization);
        securityEvents.audit(actorId, "ORGANIZATION_STATUS_CHANGE", "Organization", organizationId,
                "Changed organization status of " + organization.getCode() + " from " + previous + " to "
                        + status);
        return getOrganization(organizationId);
    }

    @Transactional(readOnly = true)
    public PlatformDtos.OrganizationDetail getOrganization(UUID organizationId) {
        Organization organization = requireBusinessOrganization(organizationId);
        long adminCount = userRoleRepository.countActiveAssignmentsByRoleCodeInOrganization(ADMIN_ROLE_CODE,
                organizationId);
        long userCount = userRepository.countByOrganizationIdAndStatus(organizationId, Status.ACTIVE);
        String primaryAdminEmail = organization.getPrimaryAdminUserId() == null
                ? null
                : userRepository.findById(organization.getPrimaryAdminUserId())
                        .map(User::getEmail)
                        .orElse(null);
        return new PlatformDtos.OrganizationDetail(organization.getId(), organization.getCode(),
                organization.getName(), organization.getDescription(), organization.getStatus(), adminCount,
                userCount, organization.getAddressLine1(), organization.getAddressLine2(), organization.getCity(),
                organization.getStateRegion(), organization.getPostalCode(), organization.getCountry(),
                organization.getContactEmail(), organization.getContactPhone(), organization.getWebsiteUrl(),
                organization.getLogoUrl(), organization.getPrimaryAdminUserId(), primaryAdminEmail);
    }

    /**
     * Creates the (single) ADMIN of a business organization ({@code specs/05 §72.5}).
     *
     * <p>Enforces the {@code MAX_ADMINS_PER_ORG} cap before any write; the created account starts
     * with {@code must_change_password = true} and a random credential, and its first password is
     * delivered as a reset link through {@link PasswordResetService} (ADR-0034).
     */
    @Transactional
    public PlatformDtos.AdminSummary createAdmin(UUID organizationId, PlatformDtos.CreateAdminRequest request,
            UUID actorId) {
        Organization organization = requireBusinessOrganization(organizationId);
        long maxAdmins = maxAdminsPerOrg();
        long existingAdmins = userRoleRepository.countActiveAssignmentsByRoleCodeInOrganization(ADMIN_ROLE_CODE,
                organizationId);
        if (existingAdmins >= maxAdmins) {
            throw new ConflictException(
                    "This organization already has its maximum of " + maxAdmins + " administrator(s)");
        }

        String email = normalizer.normalizeEmail(request.email());
        String mobile = normalizer.normalizeMobile(request.mobile());
        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("A user with this email already exists");
        }
        if (mobile != null && userRepository.existsByMobile(mobile)) {
            throw new ConflictException("A user with this mobile already exists");
        }
        Role adminRole = roleRepository.findByCodeAndOrganizationIdIsNull(ADMIN_ROLE_CODE)
                .filter(role -> role.getStatus() == Status.ACTIVE)
                .orElseThrow(() -> new IllegalStateException("ADMIN role is not seeded or active"));

        User admin = new User();
        admin.setOrganizationId(organization.getId());
        admin.setFirstName(request.firstName().trim());
        admin.setLastName(request.lastName().trim());
        admin.setEmail(email);
        admin.setMobile(mobile);
        admin.setPassword(passwordEncoder.encode(randomSecret()));
        admin.setMustChangePassword(true);
        admin.setStatus(Status.ACTIVE);

        User saved;
        try {
            saved = userRepository.save(admin);
            UserRole assignment = new UserRole();
            assignment.setUserId(saved.getId());
            assignment.setRoleId(adminRole.getId());
            assignment.setStatus(Status.ACTIVE);
            userRoleRepository.save(assignment);
        } catch (DataIntegrityViolationException race) {
            throw new ConflictException("A user with this email or mobile already exists");
        }
        passwordResetService.deliverFirstPasswordInvitation(saved);

        // Primary designation (ADR-0052, ADR-0054):
        //   * explicit request -> designate, replacing any existing primary;
        //   * otherwise, only when the organization has none -- the escape from ADR-0052's fail-closed
        //     state, without which an organization could never recover from having no primary.
        boolean explicitPrimary = Boolean.TRUE.equals(request.makePrimary());
        if (explicitPrimary && !authorizationService.hasPermission(actorId, PRIMARY_ASSIGN_PERMISSION)) {
            // Choosing the primary is a stronger act than creating an administrator, so it is gated
            // separately (V43). Creating the first administrator for an organization with no primary
            // is NOT gated: it is the recovery path out of the fail-closed state (ADR-0052).
            throw new AuthorizationException(
                    "Designating a primary administrator requires " + PRIMARY_ASSIGN_PERMISSION);
        }
        boolean designate = explicitPrimary || organization.getPrimaryAdminUserId() == null;
        String previousPrimaryEmail = null;
        if (designate) {
            previousPrimaryEmail = replacedPrimaryEmail(organization);
            organization.setPrimaryAdminUserId(saved.getId());
            organizationRepository.save(organization);
            securityEvents.audit(actorId, "PRIMARY_ADMIN_ASSIGN", "Organization", organization.getId(),
                    "Designated " + saved.getEmail() + " as primary administrator"
                            + (previousPrimaryEmail == null ? "" : ", replacing " + previousPrimaryEmail));
        }

        securityEvents.audit(actorId, "USER_CREATE", "User", saved.getId(),
                "Created organization administrator " + saved.getEmail());
        securityEvents.audit(actorId, "ADMIN_ASSIGN", "UserRole", saved.getId(),
                "Assigned ADMIN in organization " + organization.getCode());
        return new PlatformDtos.AdminSummary(saved.getId(), saved.getEmail(), Instant.now(), designate);
    }

    /**
     * Lists an organization's administrators (ADR-0053, {@code GET .../admins}).
     *
     * <p>Org-scoped by construction: the query joins assignments for the resolved organization, so a
     * caller cannot widen it by passing a different identifier. The {@code search} term filters email
     * and name, matching how the platform user list already behaves.
     *
     * <p>{@code status} defaults to "everything except ARCHIVED" (ADR-0054). Archiving keeps the role
     * assignment so it can be restored, so without this default every archived administrator would
     * reappear in the list as though still serving — the confusing half of a soft delete.
     */
    @Transactional(readOnly = true)
    public PageResponse<PlatformDtos.AdminListEntry> listAdmins(UUID organizationId, String search,
            Status status, Pageable pageable) {
        Organization organization = requireBusinessOrganization(organizationId);
        UUID primaryId = organization.getPrimaryAdminUserId();
        String term = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);

        // Resolve administrator user ids first, then page over users. Doing it as two steps keeps the
        // role join out of the paging query, so the total count cannot be inflated by a user holding
        // the role twice (inactive + active rows).
        List<UUID> adminIds = userRoleRepository.findActiveUserIdsByRoleCodeInOrganization(ADMIN_ROLE_CODE,
                organizationId);
        if (adminIds.isEmpty()) {
            // Explicit type witness: Page.empty is untyped, and a raw Page here would erase the
            // element type the mapper needs.
            return PageResponse.from(Page.<User>empty(pageable),
                    user -> toAdminEntry(user, primaryId, List.of()));
        }
        Specification<User> specification = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(root.get("id").in(adminIds));
            if (status != null) {
                // ADR-0054: archived administrators stay listable so they can be un-archived, and the
                // operator asks for them explicitly rather than having them silently mixed in with
                // current administrators (which would also make the "showing X of Y" arithmetic lie).
                predicates.add(cb.equal(root.get("status"), status));
            } else {
                predicates.add(cb.notEqual(root.get("status"), Status.ARCHIVED));
            }
            if (!term.isEmpty()) {
                String like = "%" + term + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("email")), like),
                        cb.like(cb.lower(root.get("firstName")), like),
                        cb.like(cb.lower(root.get("lastName")), like)));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        Page<User> page = userRepository.findAll(specification, pageable);
        return PageResponse.from(page, user -> toAdminEntry(user, primaryId, extraRolesOf(user.getId())));
    }

    /**
     * Replaces an administrator's non-ADMIN roles (ADR-0053, {@code PATCH .../admins/{userId}/roles}).
     *
     * <p>Deliberately refuses to add or remove ADMIN here: administrator membership is governed by the
     * {@code MAX_ADMINS_PER_ORG} cap and the primary-admin rule, and letting this endpoint touch it
     * would be a second, unaudited path to the same privileged state. It also refuses SUPER_ADMIN
     * outright, since a platform-tier capability must never be grantable from an organization screen.
     */
    @Transactional
    public PlatformDtos.AdminListEntry updateAdminRoles(UUID organizationId, UUID userId,
            PlatformDtos.UpdateAdminRolesRequest request, UUID actorId) {
        Organization organization = requireBusinessOrganization(organizationId);
        User target = requireOrganizationAdmin(organization, userId);
        requireNotArchived(target, "change the roles of");

        List<String> desiredCodes = request.roles().stream()
                .filter(code -> code != null && !code.isBlank())
                .map(code -> code.trim().toUpperCase(Locale.ROOT))
                .distinct()
                .toList();
        if (desiredCodes.stream().anyMatch(code -> ADMIN_ROLE_CODE.equals(code)
                || "SUPER_ADMIN".equals(code))) {
            throw new ConflictException(
                    "Administrator membership is governed by the organization rule and cannot be changed here");
        }

        // Resolve the whole set first, then check the whole set, then mutate. Validating as we went
        // would allow a partially-invalid request to leave the account half-changed.
        List<Role> resolved = new ArrayList<>();
        for (String code : desiredCodes) {
            Role role = roleRepository.findByCodeAndOrganizationIdIsNull(code)
                    .filter(candidate -> candidate.getStatus() == Status.ACTIVE)
                    .orElseThrow(() -> new ConflictException("Unknown role: " + code));
            resolved.add(role);
        }

        // D-08 Rule A (ADR-0044): a caller may not hand out a role carrying permissions it does not
        // hold. Without this the platform role endpoint would be a privilege-escalation hole that the
        // equivalent organization-side path (UserService.replaceRoles) already closes.
        permissionPolicy.checkAssignable(resolved.stream().map(Role::getId).toList(), actorId);

        // Keep the ADMIN assignment untouched; rewrite only the others.
        Role adminRole = roleRepository.findByCodeAndOrganizationIdIsNull(ADMIN_ROLE_CODE)
                .orElseThrow(() -> new IllegalStateException("ADMIN role is not seeded"));
        Set<UUID> desiredIds = resolved.stream().map(Role::getId).collect(Collectors.toCollection(HashSet::new));
        for (UserRole existing : userRoleRepository.findByUserIdAndStatus(userId, Status.ACTIVE)) {
            if (existing.getRoleId().equals(adminRole.getId())) {
                continue;
            }
            if (!desiredIds.contains(existing.getRoleId())) {
                existing.setStatus(Status.INACTIVE);
                userRoleRepository.save(existing);
            }
        }
        for (Role role : resolved) {
            UserRole assignment = userRoleRepository.findByUserIdAndRoleId(userId, role.getId())
                    .orElseGet(() -> {
                        UserRole created = new UserRole();
                        created.setUserId(userId);
                        created.setRoleId(role.getId());
                        return created;
                    });
            assignment.setStatus(Status.ACTIVE);
            userRoleRepository.save(assignment);
        }

        securityEvents.audit(actorId, "ADMIN_ROLE_UPDATE", "User", userId,
                "Set non-administrator roles of " + target.getEmail() + " to " + desiredCodes);
        return toAdminEntry(target, organization.getPrimaryAdminUserId(), desiredCodes);
    }

    /**
     * Archives an organization administrator (ADR-0054, {@code DELETE .../admins/{userId}}).
     *
     * <p>Archive, never delete: the account owns audit history and may own content, and
     * {@code specs/10-P1} treats user rows as retained. Archiving is the platform-facing form of
     * retirement, and it is reversible ({@link #restoreAdmin}) precisely because it is history.
     *
     * <p>Two details make it correct rather than cosmetic:
     *
     * <ul>
     *   <li>The {@code user_role} assignment is deliberately <b>left ACTIVE</b>. Un-archive must restore
     *       the administrator without replaying a snapshot, and the cap query excludes archived
     *       accounts (ADR-0054), so a retained assignment does not occupy a slot.
     *   <li>Sessions are expired and the permission cache evicted. Authorization does not re-check user
     *       status per request — only the cached permission set — so without this an archived
     *       administrator would keep full access until their token expired.
     * </ul>
     *
     * <p>Refusals: the primary administrator cannot be archived (nobody could then edit roles), and the
     * last non-archived administrator cannot be archived.
     */
    @Transactional
    public void archiveAdmin(UUID organizationId, UUID userId, UUID actorId) {
        Organization organization = requireBusinessOrganization(organizationId);
        User target = requireOrganizationAdmin(organization, userId);
        requireNotArchived(target, "archive");

        if (userId.equals(organization.getPrimaryAdminUserId())) {
            throw new ConflictException(
                    "The primary administrator cannot be archived. Designate another administrator as"
                            + " primary first.");
        }
        long activeAdmins = userRoleRepository.countActiveAssignmentsByRoleCodeInOrganization(ADMIN_ROLE_CODE,
                organizationId);
        if (activeAdmins <= 1) {
            throw new ConflictException("An organization must keep at least one active administrator");
        }

        target.setStatus(Status.ARCHIVED);
        userRepository.save(target);
        // Without these two the archived account keeps its cached permissions and live sessions:
        // authorization is decided from the permission cache, not by re-reading user status.
        sessionService.expireAllForUser(userId);
        authorizationService.evictAfterCommit(userId);
        securityEvents.audit(actorId, "ADMIN_ARCHIVE", "User", userId,
                "Archived organization administrator " + target.getEmail());
    }

    /**
     * Restores an archived organization administrator (ADR-0054, {@code POST .../admins/{userId}/restore}).
     *
     * <p>Sets the account ACTIVE and guarantees the ADMIN assignment is ACTIVE, so the account is again
     * an administrator of the organization. Extra roles are left as they were archived and remain
     * editable through {@link #updateAdminRoles} — the product decision was to restore the
     * administrator and let roles be curated, not to replay a snapshot.
     *
     * <p>Refuses when the account is not archived: restoring is a recovery action, and silently
     * resurrecting an account archived for an unrelated reason (or a never-archived one) would hide an
     * operator mistake.
     */
    @Transactional
    public PlatformDtos.AdminListEntry restoreAdmin(UUID organizationId, UUID userId, UUID actorId) {
        Organization organization = requireBusinessOrganization(organizationId);
        User target = requireOrganizationAdmin(organization, userId);
        if (target.getStatus() != Status.ARCHIVED) {
            throw new ConflictException("This administrator is not archived");
        }

        Role adminRole = roleRepository.findByCodeAndOrganizationIdIsNull(ADMIN_ROLE_CODE)
                .orElseThrow(() -> new IllegalStateException("ADMIN role is not seeded"));
        UserRole assignment = userRoleRepository.findByUserIdAndRoleId(userId, adminRole.getId())
                .orElseGet(() -> {
                    UserRole created = new UserRole();
                    created.setUserId(userId);
                    created.setRoleId(adminRole.getId());
                    return created;
                });
        assignment.setStatus(Status.ACTIVE);
        userRoleRepository.save(assignment);

        target.setStatus(Status.ACTIVE);
        userRepository.save(target);
        authorizationService.evictAfterCommit(userId);
        securityEvents.audit(actorId, "ADMIN_RESTORE", "User", userId,
                "Restored organization administrator " + target.getEmail());
        return toAdminEntry(target, organization.getPrimaryAdminUserId(), extraRolesOf(userId));
    }

    /**
     * Designates which administrator may change other users' roles (ADR-0054, §72.14).
     *
     * <p>This is the transfer that ADR-0052 left unimplemented (GAP-197). Without it the primary was
     * effectively permanent: it was set by the first create and could only change by deleting the user.
     *
     * <p>Refusals, each for a reason:
     *
     * <ul>
     *   <li>A user of another organization answers <b>404, never 403</b> — the cross-tenant rule from
     *       §72.13; a 403 would confirm the id exists elsewhere.
     *   <li>An <b>archived</b> account is refused with 409. Designating an archived administrator would
     *       point the authority for role editing at an account that cannot log in, locking the
     *       organization out of its own administration.
     *   <li>Designating the current primary is a <b>successful no-op</b>, not an error: the call is
     *       idempotent so a retry after a dropped response is safe.
     * </ul>
     */
    @Transactional
    public PlatformDtos.OrganizationDetail setPrimaryAdmin(UUID organizationId, UUID userId, UUID actorId) {
        Organization organization = requireBusinessOrganization(organizationId);
        User target = requireOrganizationAdmin(organization, userId);
        if (target.getStatus() == Status.ARCHIVED) {
            throw new ConflictException("An archived administrator cannot be the primary administrator");
        }
        if (userId.equals(organization.getPrimaryAdminUserId())) {
            // Idempotent: re-designating the current primary is what a retry looks like.
            return getOrganization(organizationId);
        }

        String previousEmail = replacedPrimaryEmail(organization);
        organization.setPrimaryAdminUserId(userId);
        organizationRepository.save(organization);
        securityEvents.audit(actorId, "PRIMARY_ADMIN_TRANSFER", "Organization", organization.getId(),
                "Transferring primary administrator from "
                        + (previousEmail == null ? "(none)" : previousEmail) + " to " + target.getEmail());
        return getOrganization(organizationId);
    }

    /**
     * Refuses an operation on an archived administrator.
     *
     * <p>Separate from {@link #requireOrganizationAdmin} because that method must <em>find</em> archived
     * accounts (restore needs them); whether an archived account may be acted on is a per-operation
     * question with its own message.
     */
    private void requireNotArchived(User user, String action) {
        if (user.getStatus() == Status.ARCHIVED) {
            throw new ConflictException(
                    "Cannot " + action + " an archived administrator. Restore the account first.");
        }
    }

    /** The email of the currently designated primary, or {@code null}. Used for audit remarks. */
    private String replacedPrimaryEmail(Organization organization) {
        UUID current = organization.getPrimaryAdminUserId();
        return current == null ? null : userRepository.findById(current).map(User::getEmail).orElse(null);
    }

    /**
     * Resolves a user and proves they are an administrator of this organization.
     *
     * <p>Accepts an <b>archived</b> administrator: the role assignment survives archiving (ADR-0054),
     * so restore and un-archive would otherwise be unreachable — they would 404 on the very rows they
     * exist to fix. Callers that need "currently serving" (role editing, primacy transfer, archiving)
     * check {@code status} themselves and refuse with a specific message.
     */
    private User requireOrganizationAdmin(Organization organization, UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> NotFoundException.of("User", userId));
        // A user from another organization is reported as absent, not forbidden: a 403 would confirm
        // that the id exists somewhere, which is exactly the cross-tenant leak to avoid.
        if (!organization.getId().equals(user.getOrganizationId())) {
            throw NotFoundException.of("User", userId);
        }
        Role adminRole = roleRepository.findByCodeAndOrganizationIdIsNull(ADMIN_ROLE_CODE)
                .orElseThrow(() -> new IllegalStateException("ADMIN role is not seeded"));
        boolean isAdmin = userRoleRepository.findByUserIdAndRoleId(userId, adminRole.getId())
                .filter(assignment -> assignment.getStatus() == Status.ACTIVE)
                .isPresent();
        if (!isAdmin) {
            throw NotFoundException.of("User", userId);
        }
        return user;
    }

    private PlatformDtos.AdminListEntry toAdminEntry(User user, UUID primaryId, List<String> roles) {
        return new PlatformDtos.AdminListEntry(user.getId(), user.getFirstName(), user.getLastName(),
                user.getEmail(), user.getStatus(), user.getId().equals(primaryId), user.getCreatedOn(), roles);
    }

    /** Role codes other than ADMIN, so the list shows what else the account carries. */
    private List<String> extraRolesOf(UUID userId) {
        return userRoleRepository.findByUserIdAndStatus(userId, Status.ACTIVE).stream()
                .map(assignment -> roleRepository.findById(assignment.getRoleId()).map(Role::getCode).orElse(""))
                .filter(code -> !code.isEmpty() && !ADMIN_ROLE_CODE.equals(code))
                .sorted()
                .toList();
    }

    private Organization requireBusinessOrganization(UUID organizationId) {
        Organization organization = organizationRepository.findById(organizationId)
                .orElseThrow(() -> NotFoundException.of("Organization", organizationId));
        if (PLATFORM_CODE.equals(organization.getCode())) {
            throw NotFoundException.of("Organization", organizationId);
        }
        return organization;
    }

    private long maxAdminsPerOrg() {
        return ruleResolver.resolve(MAX_ADMINS_RULE)
                .flatMap(rule -> rule.decimal("maxAdmins"))
                .map(java.math.BigDecimal::longValue)
                .filter(value -> value >= 0)
                .orElse(DEFAULT_MAX_ADMINS);
    }

    private String normalizeCode(String code) {
        return code == null ? null : code.trim().toUpperCase(Locale.ROOT);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String randomSecret() {
        byte[] buffer = new byte[32];
        new SecureRandom().nextBytes(buffer);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buffer);
    }
}
