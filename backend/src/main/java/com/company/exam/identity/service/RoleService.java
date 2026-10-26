package com.company.exam.identity.service;

import com.company.exam.common.api.ApiError;
import com.company.exam.common.enums.Status;
import com.company.exam.common.exception.ConflictException;
import com.company.exam.common.exception.InvalidStateException;
import com.company.exam.common.exception.NotFoundException;
import com.company.exam.common.exception.ValidationException;
import com.company.exam.identity.dto.PermissionDtos;
import com.company.exam.identity.dto.RoleDtos;
import com.company.exam.identity.entity.Permission;
import com.company.exam.identity.entity.Role;
import com.company.exam.identity.entity.RolePermission;
import com.company.exam.identity.entity.UserRole;
import com.company.exam.identity.exception.RoleImmutabilityException;
import com.company.exam.identity.exception.SelfPermissionModificationException;
import com.company.exam.identity.mapper.IdentityMapper;
import com.company.exam.identity.repository.PermissionRepository;
import com.company.exam.identity.repository.RolePermissionRepository;
import com.company.exam.identity.repository.RoleRepository;
import com.company.exam.identity.repository.UserRoleRepository;
import com.company.exam.common.audit.SecurityEventLogger;
import com.company.exam.identity.IdentityConstants;
import com.company.exam.identity.validation.IdentityNormalizer;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Role management and transactional permission replacement
 * ({@code specs/10-P1-Identity-V1.5.md §60, §61, §83}).
 *
 * <p>Permission replacement is a single transactional operation: a failure leaves the previous
 * assignment state intact ({@code §61, §62}). The authorization cache is invalidated only after
 * the transaction commits ({@code §72}).
 */
@Service
public class RoleService {

    private final RoleRepository roleRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final PermissionRepository permissionRepository;
    private final UserRoleRepository userRoleRepository;
    private final IdentityNormalizer normalizer;
    private final IdentityMapper mapper;
    private final AuthorizationService authorizationService;
    private final SecurityEventLogger securityEvents;
    private final RolePermissionPolicy permissionPolicy;
    private final com.company.exam.identity.repository.UserRepository userRepository;


    public RoleService(RoleRepository roleRepository, RolePermissionRepository rolePermissionRepository,
            PermissionRepository permissionRepository, UserRoleRepository userRoleRepository,
            IdentityNormalizer normalizer, IdentityMapper mapper, AuthorizationService authorizationService,
            SecurityEventLogger securityEvents, RolePermissionPolicy permissionPolicy,
            com.company.exam.identity.repository.UserRepository userRepository) {
        this.roleRepository = roleRepository;
        this.rolePermissionRepository = rolePermissionRepository;
        this.permissionRepository = permissionRepository;
        this.userRoleRepository = userRoleRepository;
        this.normalizer = normalizer;
        this.mapper = mapper;
        this.authorizationService = authorizationService;
        this.securityEvents = securityEvents;
        this.permissionPolicy = permissionPolicy;
        this.userRepository = userRepository;
    }

    /**
     * The role catalogue visible to the caller: platform-wide roles plus its own organization's
     * (ADR-0045, D-05).
     *
     * <p>Before this, every organization saw every role, so one organization's private role appeared
     * in another's catalogue. The scope comes from the actor, never from a request parameter.
     */
    @Transactional(readOnly = true)
    public Page<RoleDtos.RoleSummary> list(Pageable pageable, UUID actorId) {
        UUID organizationId = organizationOf(actorId);
        if (organizationId == null) {
            // Fail closed (G-10): an unattributable actor sees nothing rather than the installation.
            return Page.empty(pageable);
        }
        if (isSystemAdministrator(actorId)) {
            // R1: SUPER_ADMIN administers the whole installation, so it sees every role, including
            // organization-owned ones. Scoping it to its own organization would hide every business
            // role the moment an organization created one — a regression against R1 that the first
            // version of this method introduced.
            //
            // The test is role membership, not PLATFORM-organization residence: ADR-0032 permits
            // exactly one SUPER_ADMIN account and the bootstrap check is keyed on the PLATFORM
            // organization, so a residency-based rule is both narrower than the requirement and
            // awkward to test without violating that invariant.
            List<Role> all = roleRepository.findAll();
            List<RoleDtos.RoleSummary> allSummaries = all.stream().map(mapper::toSummary).toList();
            int allFrom = Math.min((int) pageable.getOffset(), allSummaries.size());
            int allTo = Math.min(allFrom + pageable.getPageSize(), allSummaries.size());
            return new PageImpl<>(allSummaries.subList(allFrom, allTo), pageable, allSummaries.size());
        }
        List<Role> visible = roleRepository
                .findByOrganizationIdIsNullOrOrganizationId(organizationId, organizationId);
        List<RoleDtos.RoleSummary> summaries = visible.stream().map(mapper::toSummary).toList();
        int from = Math.min((int) pageable.getOffset(), summaries.size());
        int to = Math.min(from + pageable.getPageSize(), summaries.size());
        return new PageImpl<>(summaries.subList(from, to), pageable, summaries.size());
    }

    /**
     * Creates a role owned by the caller's organization (ADR-0045).
     *
     * <p>A platform-wide role has no owner and is therefore not creatable through the API: granting
     * that would let an organization mint a role every other organization must then see, which is
     * D-05 in a new form. {@code SUPER_ADMIN} is the only caller that legitimately needs
     * installation-wide roles, and it does so through seeds and migrations, not through this method.
     */
    @Transactional
    public RoleDtos.RoleSummary create(RoleDtos.CreateRoleRequest request, UUID actorId) {
        String code = normalizer.normalizeRoleCode(request.code());
        UUID organizationId = organizationOf(actorId);
        if (organizationId == null) {
            throw new NotFoundException("The caller's organization could not be resolved");
        }
        if (roleRepository.existsByCodeAndOrganizationId(code, organizationId)) {
            throw new ConflictException("A role with this code already exists");
        }
        Role role = new Role();
        role.setCode(code);
        role.setOrganizationId(organizationId);
        role.setName(request.name().trim());
        role.setDescription(request.description());
        role.setStatus(Status.ACTIVE);
        try {
            return mapper.toSummary(roleRepository.save(role));
        } catch (DataIntegrityViolationException race) {
            throw new ConflictException("A role with this code already exists");
        }
    }

    @Transactional
    public RoleDtos.RoleSummary update(UUID id, RoleDtos.UpdateRoleRequest request, UUID actorId) {
        Role role = roleRepository.findById(id).orElseThrow(() -> NotFoundException.of("Role", id));
        guardRoleImmutability(role);
        if (request.name() != null) {
            role.setName(request.name().trim());
        }
        if (request.description() != null) {
            role.setDescription(request.description());
        }
        if (request.status() != null && request.status() != role.getStatus()) {
            // D-07: a caller may not change the status of a role it actively holds. Renaming or
            // re-describing is cosmetic and stays allowed; status is not, because deactivating the
            // role removes the very permissions the caller would need to undo it.
            if (actorId != null && userRoleRepository.findByUserIdAndRoleId(actorId, id)
                    .filter(userRole -> userRole.getStatus() == Status.ACTIVE).isPresent()) {
                throw new SelfPermissionModificationException();
            }
            role.setStatus(request.status());
            if (request.status() != Status.ACTIVE) {
                evictUsersOfRole(id);
            }
        }
        return mapper.toSummary(roleRepository.save(role));
    }

    @Transactional(readOnly = true)
    public List<PermissionDtos.PermissionSummary> permissions(UUID roleId) {
        roleRepository.findById(roleId).orElseThrow(() -> NotFoundException.of("Role", roleId));
        return permissionRepository.findEffectiveByRoleId(roleId).stream().map(mapper::toSummary).toList();
    }

    @Transactional
    public List<PermissionDtos.PermissionSummary> replacePermissions(UUID roleId, List<UUID> permissionIds,
            UUID actorId) {
        Role role = roleRepository.findById(roleId).orElseThrow(() -> NotFoundException.of("Role", roleId));
        // Immutability is checked before the ACTIVE check and before the self-membership check: a
        // contract-fixed role is refused for every caller, including SUPER_ADMIN, and the refusal
        // must not vary with the caller's own membership or with the role's current status.
        guardRoleImmutability(role);
        if (role.getStatus() != Status.ACTIVE) {
            throw new InvalidStateException("Permissions cannot be changed on an inactive role");
        }
        if (actorId != null && userRoleRepository.findByUserIdAndRoleId(actorId, roleId)
                .filter(userRole -> userRole.getStatus() == Status.ACTIVE).isPresent()) {
            throw new SelfPermissionModificationException();
        }
        List<Permission> desiredPermissions = validatePermissions(permissionIds);
        // Guard precedence is fixed by ADR-0038 and asserted test-by-test: immutability, then
        // reserved set, then bounded delegation. A later refusal must never mask an earlier one.
        permissionPolicy.checkReserved(desiredPermissions, actorId);
        permissionPolicy.checkDelegation(desiredPermissions, actorId);
        Set<UUID> desired = desiredPermissions.stream().map(Permission::getId).collect(Collectors.toSet());

        List<RolePermission> existing = rolePermissionRepository.findByRoleId(roleId);
        Set<UUID> existingIds = existing.stream().map(RolePermission::getPermissionId).collect(Collectors.toSet());
        for (RolePermission rolePermission : existing) {
            Status target = desired.contains(rolePermission.getPermissionId()) ? Status.ACTIVE : Status.INACTIVE;
            if (rolePermission.getStatus() != target) {
                rolePermission.setStatus(target);
                rolePermissionRepository.save(rolePermission);
            }
        }
        for (UUID permissionId : desired) {
            if (!existingIds.contains(permissionId)) {
                RolePermission rolePermission = new RolePermission();
                rolePermission.setRoleId(roleId);
                rolePermission.setPermissionId(permissionId);
                rolePermission.setStatus(Status.ACTIVE);
                rolePermissionRepository.save(rolePermission);
            }
        }

        evictUsersOfRole(roleId);
        securityEvents.roleChanged(actorId, null, "ROLE_PERMISSION_REPLACE",
                "Replaced permissions for role " + role.getCode());
        return permissionRepository.findEffectiveByRoleId(roleId).stream().map(mapper::toSummary).toList();
    }

    /**
     * Refuses any mutation of a contract-fixed role ({@code 20-P11} §18, ADR-0032, ADR-0038).
     *
     * <p>There is deliberately no caller exemption. ADR-0032 makes {@code SUPER_ADMIN} permanent,
     * unique and non-demotable; a guard that let SUPER_ADMIN edit its own role would make the
     * invariant conditional on the very principal it protects.
     */
    /** True when the actor holds the SUPER_ADMIN role, i.e. is the system administrator (R1). */
    private boolean isSystemAdministrator(UUID actorId) {
        if (actorId == null) {
            return false;
        }
        return roleRepository.findByCodeAndOrganizationIdIsNull(IdentityConstants.SUPER_ADMIN_ROLE_CODE)
                .map(Role::getId)
                .filter(roleId -> userRoleRepository.findByUserIdAndRoleId(actorId, roleId)
                        .filter(userRole -> userRole.getStatus() == Status.ACTIVE).isPresent())
                .isPresent();
    }

    /** The acting user's organization id, or {@code null} when it cannot be resolved (fail closed). */
    private UUID organizationOf(UUID actorId) {
        if (actorId == null) {
            return null;
        }
        return userRepository.findById(actorId)
                .map(com.company.exam.identity.entity.User::getOrganizationId)
                .orElse(null);
    }

    private void guardRoleImmutability(Role role) {
        if (IdentityConstants.IMMUTABLE_ROLE_CODES.contains(role.getCode())) {
            throw new RoleImmutabilityException();
        }
    }

    private List<Permission> validatePermissions(List<UUID> permissionIds) {
        List<UUID> distinct = permissionIds.stream().distinct().toList();
        if (distinct.isEmpty()) {
            return List.of();
        }
        List<Permission> found = permissionRepository.findAllByIdIn(distinct);
        if (found.size() != distinct.size()) {
            throw new ValidationException("Validation failed",
                    List.of(ApiError.of("permissionIds", "INVALID_PERMISSION",
                            "One or more permissions do not exist")));
        }
        return found;
    }

    private void evictUsersOfRole(UUID roleId) {
        for (UserRole userRole : userRoleRepository.findByRoleIdAndStatus(roleId, Status.ACTIVE)) {
            authorizationService.evictAfterCommit(userRole.getUserId());
        }
    }
}
