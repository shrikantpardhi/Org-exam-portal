package com.company.exam.identity.service;

import com.company.exam.common.enums.Status;
import com.company.exam.identity.api.CandidateDirectory;
import com.company.exam.identity.entity.UserRole;
import com.company.exam.identity.repository.RoleRepository;
import com.company.exam.identity.repository.UserRepository;
import com.company.exam.identity.repository.UserRoleRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Default {@link CandidateDirectory} ({@code specs/14-P5-Delivery-V1.5.md §19}).
 *
 * <p>The pilot candidate set is the ACTIVE users holding the {@code STUDENT} role. It is a bounded,
 * operational set and is replaced by the AccessGrant-driven population when Commerce is implemented.
 */
@Service
public class CandidateDirectoryService implements CandidateDirectory {

    private static final String STUDENT_ROLE = "STUDENT";

    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final UserRepository userRepository;

    public CandidateDirectoryService(RoleRepository roleRepository, UserRoleRepository userRoleRepository,
            UserRepository userRepository) {
        this.roleRepository = roleRepository;
        this.userRoleRepository = userRoleRepository;
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> activeCandidateUserIds() {
        return roleRepository.findByCodeAndOrganizationIdIsNull(STUDENT_ROLE)
                .map(role -> userRoleRepository.findByRoleIdAndStatus(role.getId(), Status.ACTIVE).stream()
                        .map(UserRole::getUserId)
                        .distinct()
                        .filter(userId -> userRepository.findByIdAndStatus(userId, Status.ACTIVE).isPresent())
                        .toList())
                .orElseGet(List::of);
    }
}
