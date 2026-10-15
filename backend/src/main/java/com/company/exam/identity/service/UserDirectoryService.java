package com.company.exam.identity.service;

import com.company.exam.identity.api.UserDirectory;
import com.company.exam.identity.repository.UserRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Default {@link UserDirectory} ({@code specs/15-P6-Evaluation-V1.5.md §99}). */
@Service
public class UserDirectoryService implements UserDirectory {

    private final UserRepository userRepository;

    public UserDirectoryService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UserView> find(UUID userId) {
        return userRepository.findById(userId)
                .map(user -> new UserView(user.getId(), user.getFirstName(), user.getLastName(), user.getEmail()));
    }
}
