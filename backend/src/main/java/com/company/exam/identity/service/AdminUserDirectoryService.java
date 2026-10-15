package com.company.exam.identity.service;

import com.company.exam.common.audit.SecurityEventLogger;
import com.company.exam.common.enums.Status;
import com.company.exam.common.exception.NotFoundException;
import com.company.exam.common.exception.ValidationException;
import com.company.exam.identity.api.AdminUserDirectory;
import com.company.exam.identity.entity.User;
import com.company.exam.identity.repository.UserRepository;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Default {@link AdminUserDirectory} ({@code specs/20-P11-Administration-V1.5.md §12-§16}).
 *
 * <p>Identity owns the user lifecycle and remains the only writer of the {@code user} table; this
 * service is the contract Administration calls.
 */
@Service
public class AdminUserDirectoryService implements AdminUserDirectory {

    private final UserRepository userRepository;
    private final SecurityEventLogger audit;

    public AdminUserDirectoryService(UserRepository userRepository, SecurityEventLogger audit) {
        this.userRepository = userRepository;
        this.audit = audit;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AdminUserView> find(UUID userId) {
        return userRepository.findById(userId).map(this::toView);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AdminUserView> search(String query, int limit) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        String needle = query.trim();
        String lowered = needle.toLowerCase(Locale.ROOT);
        return userRepository
                .findAll((root, criteriaQuery, builder) -> {
                    List<Predicate> predicates = new ArrayList<>();
                    predicates.add(builder.like(builder.lower(root.get("firstName")), "%" + lowered + "%"));
                    predicates.add(builder.like(builder.lower(root.get("lastName")), "%" + lowered + "%"));
                    predicates.add(builder.equal(builder.lower(root.get("email")), lowered));
                    predicates.add(builder.equal(root.get("mobile"), needle));
                    return builder.or(predicates.toArray(Predicate[]::new));
                }, PageRequest.of(0, Math.max(1, Math.min(limit, 100))))
                .map(this::toView)
                .toList();
    }

    @Override
    @Transactional
    public void setLifecycle(UUID userId, String status, UUID actorId) {
        User user = userRepository.findById(userId).orElseThrow(() -> NotFoundException.of("User", userId));
        Status target;
        try {
            target = Status.valueOf(status);
        } catch (IllegalArgumentException unknown) {
            throw new ValidationException("Unsupported lifecycle state: " + status);
        }
        user.setStatus(target);
        userRepository.saveAndFlush(user);
        audit.audit(actorId, target == Status.ACTIVE ? "ACTIVATE" : "ARCHIVE", "User", user.getId(),
                "Identity lifecycle set to " + target);
    }

    @Override
    @Transactional(readOnly = true)
    public long countActiveUsers() {
        return userRepository.count((root, query, builder) -> builder.equal(root.get("status"), Status.ACTIVE));
    }

    private AdminUserView toView(User user) {
        return new AdminUserView(user.getId(), user.getFirstName(), user.getLastName(), user.getEmail(),
                user.getMobile(), user.getStatus() == null ? null : user.getStatus().name());
    }
}
