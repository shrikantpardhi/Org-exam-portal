package com.company.exam.platform.service;

import com.company.exam.common.api.PageResponse;
import com.company.exam.common.api.Pagination;
import com.company.exam.common.enums.Status;
import com.company.exam.identity.entity.Organization;
import com.company.exam.identity.entity.Role;
import com.company.exam.identity.entity.User;
import com.company.exam.identity.entity.UserRole;
import com.company.exam.identity.repository.OrganizationRepository;
import com.company.exam.identity.repository.RoleRepository;
import com.company.exam.identity.repository.UserRepository;
import com.company.exam.identity.repository.UserRoleRepository;
import com.company.exam.platform.dto.PlatformDtos;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cross-organization user list for the platform tier (ADR-0042, Gate A answer A4).
 *
 * <p>Read-only by construction: no update or delete endpoint accompanies it, because SUPER_ADMIN's
 * user surface is {@code /platform/*} and mutating another organization's users is the cross-tenant
 * reach requirement R2 is about.
 *
 * <p>Unlike every business list, this one <strong>includes</strong> the {@code PLATFORM}
 * organization (ADR-0033 excludes it elsewhere). This is a platform-tier view and the installation
 * owner is a user it must be able to see; excluding it would make the list silently incomplete.
 */
@Service
public class PlatformUserService {

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final RoleRepository roleRepository;
    private final OrganizationRepository organizationRepository;

    public PlatformUserService(UserRepository userRepository, UserRoleRepository userRoleRepository,
            RoleRepository roleRepository, OrganizationRepository organizationRepository) {
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
        this.roleRepository = roleRepository;
        this.organizationRepository = organizationRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<PlatformDtos.PlatformUserSummary> listUsers(Status status, UUID organizationId,
            String search, Pageable pageable) {
        Page<User> page = userRepository.findAll(userSpecification(status, organizationId, search), pageable);
        List<User> users = page.getContent();

        Map<UUID, String> organizationCodes = organizationCodes(users);
        Map<UUID, List<String>> roleCodes = roleCodesForUsers(users);

        List<PlatformDtos.PlatformUserSummary> content = users.stream()
                .map(user -> new PlatformDtos.PlatformUserSummary(
                        user.getId(),
                        user.getFirstName(),
                        user.getLastName(),
                        user.getEmail(),
                        user.getStatus(),
                        user.getOrganizationId(),
                        organizationCodes.get(user.getOrganizationId()),
                        roleCodes.getOrDefault(user.getId(), List.of())))
                .toList();
        return new PageResponse<>(content, Pagination.from(page));
    }

    private Map<UUID, String> organizationCodes(List<User> users) {
        List<UUID> ids = users.stream().map(User::getOrganizationId).filter(Objects::nonNull)
                .distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return organizationRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Organization::getId, Organization::getCode));
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
        Map<UUID, String> codes = roleRepository
                .findAllByIdIn(assignments.stream().map(UserRole::getRoleId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Role::getId, Role::getCode));
        return assignments.stream().collect(Collectors.groupingBy(UserRole::getUserId,
                Collectors.mapping(assignment -> codes.getOrDefault(assignment.getRoleId(),
                        assignment.getRoleId().toString()), Collectors.toList())));
    }

    private Specification<User> userSpecification(Status status, UUID organizationId, String search) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (organizationId != null) {
                predicates.add(cb.equal(root.get("organizationId"), organizationId));
            }
            if (search != null && !search.isBlank()) {
                String like = "%" + search.toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("email")), like),
                        cb.like(cb.lower(root.get("firstName")), like),
                        cb.like(cb.lower(root.get("lastName")), like)));
            }
            return predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
