package com.company.exam.platform.service;

import com.company.exam.administration.api.BusinessRuleResolver;
import com.company.exam.common.audit.SecurityEventLogger;
import com.company.exam.common.api.PageResponse;
import com.company.exam.common.enums.Status;
import com.company.exam.common.exception.ConflictException;
import com.company.exam.common.exception.NotFoundException;
import com.company.exam.identity.entity.Organization;
import com.company.exam.identity.entity.Role;
import com.company.exam.identity.entity.User;
import com.company.exam.identity.entity.UserRole;
import com.company.exam.identity.repository.OrganizationRepository;
import com.company.exam.identity.repository.RoleRepository;
import com.company.exam.identity.repository.UserRepository;
import com.company.exam.identity.repository.UserRoleRepository;
import com.company.exam.identity.service.PasswordResetService;
import com.company.exam.identity.validation.IdentityNormalizer;
import com.company.exam.platform.dto.PlatformDtos;
import jakarta.persistence.criteria.Predicate;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
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

    private final OrganizationRepository organizationRepository;
    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final PasswordEncoder passwordEncoder;
    private final IdentityNormalizer normalizer;
    private final PasswordResetService passwordResetService;
    private final BusinessRuleResolver ruleResolver;
    private final SecurityEventLogger securityEvents;

    public PlatformOrganizationService(OrganizationRepository organizationRepository,
            RoleRepository roleRepository, UserRepository userRepository, UserRoleRepository userRoleRepository,
            PasswordEncoder passwordEncoder, IdentityNormalizer normalizer,
            PasswordResetService passwordResetService, BusinessRuleResolver ruleResolver,
            SecurityEventLogger securityEvents) {
        this.organizationRepository = organizationRepository;
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
        this.passwordEncoder = passwordEncoder;
        this.normalizer = normalizer;
        this.passwordResetService = passwordResetService;
        this.ruleResolver = ruleResolver;
        this.securityEvents = securityEvents;
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

    @Transactional(readOnly = true)
    public PlatformDtos.OrganizationDetail getOrganization(UUID organizationId) {
        Organization organization = requireBusinessOrganization(organizationId);
        long adminCount = userRoleRepository.countActiveAssignmentsByRoleCodeInOrganization(ADMIN_ROLE_CODE,
                organizationId);
        long userCount = userRepository.countByOrganizationIdAndStatus(organizationId, Status.ACTIVE);
        return new PlatformDtos.OrganizationDetail(organization.getId(), organization.getCode(),
                organization.getName(), organization.getDescription(), organization.getStatus(), adminCount,
                userCount, organization.getAddressLine1(), organization.getAddressLine2(), organization.getCity(),
                organization.getStateRegion(), organization.getPostalCode(), organization.getCountry(),
                organization.getContactEmail(), organization.getContactPhone(), organization.getWebsiteUrl(),
                organization.getLogoUrl());
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
        securityEvents.audit(actorId, "USER_CREATE", "User", saved.getId(),
                "Created organization administrator " + saved.getEmail());
        securityEvents.audit(actorId, "ADMIN_ASSIGN", "UserRole", saved.getId(),
                "Assigned ADMIN in organization " + organization.getCode());
        return new PlatformDtos.AdminSummary(saved.getId(), saved.getEmail(), Instant.now());
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
