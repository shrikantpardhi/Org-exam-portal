package com.company.exam.identity.service;

import com.company.exam.identity.dto.PermissionDtos;
import com.company.exam.identity.entity.Permission;
import com.company.exam.identity.mapper.IdentityMapper;
import com.company.exam.identity.repository.PermissionRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Permission catalog access ({@code specs/10-P1-Identity-V1.5.md §84}).
 *
 * <p>The catalog is read-only through the administrative API; permissions are controlled master
 * data, not something arbitrary clients mutate ({@code §52}).
 */
@Service
public class PermissionService {

    private final PermissionRepository permissionRepository;
    private final IdentityMapper mapper;

    public PermissionService(PermissionRepository permissionRepository, IdentityMapper mapper) {
        this.permissionRepository = permissionRepository;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public Page<PermissionDtos.PermissionSummary> list(Pageable pageable) {
        return permissionRepository.findAll(pageable).map(mapper::toSummary);
    }

    @Transactional(readOnly = true)
    public List<PermissionDtos.PermissionSummary> effectiveForRole(UUID roleId) {
        return permissionRepository.findEffectiveByRoleId(roleId).stream().map(mapper::toSummary).toList();
    }

    @Transactional(readOnly = true)
    public List<Permission> findAllByIds(List<UUID> ids) {
        return permissionRepository.findAllByIdIn(ids);
    }
}
