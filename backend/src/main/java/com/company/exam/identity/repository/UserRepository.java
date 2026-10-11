package com.company.exam.identity.repository;

import com.company.exam.identity.entity.User;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Persistence for {@link User} ({@code specs/10-P1-Identity-V1.5.md §8}).
 *
 * <p>Emails are normalized (lower-cased, trimmed) before they are stored, so lookups use the
 * normalized value. {@link JpaSpecificationExecutor} backs the bounded, database-side user search
 * required by {@code §55} and {@code §139}.
 */
public interface UserRepository extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByMobile(String mobile);

    Optional<User> findByIdAndStatus(UUID id, com.company.exam.common.enums.Status status);

    /**
     * Loads a user under a pessimistic write lock so concurrent password resets serialize and the
     * single-use reset binding cannot be consumed twice
     * ({@code specs/10-P1-Identity-V1.5.md §130}). Must be called inside a transaction.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") UUID id);
}
