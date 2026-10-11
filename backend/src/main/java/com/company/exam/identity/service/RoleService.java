package com.company.exam.identity.service;

import com.company.exam.common.api.ApiError;
import com.company.exam.common.enums.Status;
import com.company.exam.common.exception.ConflictException;
import com.company.exam.common.exception.InvalidStateException;
import com.company.exam.common.exception.NotFoundException;
import com.company.exam.common.exception.ValidationException;
import com.company.exam.identity.dto.PermissionDtos;
import com.company.exam.identity.dto.RoleDtos;
import com.company.exam.identity.entity.Role;
import com.company.exam.identity.entity.RolePermission;
import com.company.exam.identity.entity.UserRole;
import com.company.exam.identity.exception.SelfPermissionModificationException;
import com.company.exam.identity.mapper.IdentityMapper;
import com.company.exam.identity.repository.PermissionRepository;
import com.company.exam.identity.repository.RolePermissionRepository;
import com.company.exam.identity.repository.RoleRepository;
import com.company.exam.identity.repository.UserRoleRepository;
import com.company.exam.common.audit.SecurityEventLogger;
import com.company.exam.identity.validation.IdentityNormalizer;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
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

    public RoleService(RoleRepository roleRepository, RolePermissionRepository rolePermissionRepository,
            PermissionRepository permissionRepository, UserRoleRepository userRoleRepository,
            IdentityNormalizer normalizer, IdentityMapper mapper, AuthorizationService authorizationService,
            SecurityEventLogger securityEvents) {
        this.roleRepository = roleRepository;
        this.rolePermissionRepository = rolePermissionRepository;
        this.permissionRepository = permissionRepository;
        this.userRoleRepository = userRoleRepository;
        this.normalizer = normalizer;
        this.mapper = mapper;
        this.authorizationService = authorizationService;
        this.securityEvents = securityEvents;
    }

    @Transactional(readOnly = true)
    public Page<RoleDtos.RoleSummary> list(Pageable pageable) {
        return roleRepository.findAll(pageable).map(mapper::toSummary);
    }

    @Transactional
    public RoleDtos.RoleSummary create(RoleDtos.CreateRoleRequest request) {
        String code = normalizer.normalizeRoleCode(request.code());
        if (roleRepository.existsByCode(code)) {
            throw new ConflictException("A role with this code already exists");
        }
        Role role = new Role();
        role.setCode(code);
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
        if (request.name() != null) {
            role.setName(request.name().trim());
        }
        if (request.description() != null) {
            role.setDescription(request.description());
        }
        if (request.status() != null && request.status() != role.getStatus()) {
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
        if (role.getStatus() != Status.ACTIVE) {
            throw new InvalidStateException("Permissions cannot be changed on an inactive role");
        }
        if (actorId != null && userRoleRepository.findByUserIdAndRoleId(actorId, roleId)
                .filter(userRole -> userRole.getStatus() == Status.ACTIVE).isPresent()) {
            throw new SelfPermissionModificationException();
        }
        Set<UUID> desired = validatePermissions(permissionIds);

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

    private Set<UUID> validatePermissions(List<UUID> permissionIds) {
        List<UUID> distinct = permissionIds.stream().distinct().toList();
        if (distinct.isEmpty()) {
            return Set.of();
        }
        int found = permissionRepository.findAllByIdIn(distinct).size();
        if (found != distinct.size()) {
            throw new ValidationException("Validation failed",
                    List.of(ApiError.of("permissionIds", "INVALID_PERMISSION",
                            "One or more permissions do not exist")));
        }
        return new HashSet<>(distinct);
    }

    private void evictUsersOfRole(UUID roleId) {
        for (UserRole userRole : userRoleRepository.findByRoleIdAndStatus(roleId, Status.ACTIVE)) {
            authorizationService.evictAfterCommit(userRole.getUserId());
        }
    }
}
