package com.company.exam.identity.service;

import com.company.exam.common.audit.SecurityEventLogger;
import com.company.exam.identity.entity.User;
import com.company.exam.identity.repository.UserRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Supplies a user's organization to the shared audit logger (ADR-0046, D-11).
 *
 * <p>The logger lives in {@code common} and must not depend on Identity; Identity implements the
 * small lookup interface instead, so the dependency points inward and modules without Identity still
 * construct the logger.
 */
@Service
public class UserOrganizationLookupService implements SecurityEventLogger.UserOrganizationLookup {

    private final UserRepository userRepository;

    public UserOrganizationLookupService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public Optional<UUID> organizationOf(UUID userId) {
        if (userId == null) {
            return Optional.empty();
        }
        return userRepository.findById(userId).map(User::getOrganizationId);
    }
}
