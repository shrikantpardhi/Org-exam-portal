package com.company.exam.identity.repository;

import com.company.exam.identity.entity.Role;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Persistence for {@link Role} ({@code specs/10-P1-Identity-V1.5.md §9}). */
public interface RoleRepository extends JpaRepository<Role, UUID> {

    Optional<Role> findByCode(String code);

    boolean existsByCode(String code);

    List<Role> findAllByCodeIn(Collection<String> codes);

    List<Role> findAllByIdIn(Collection<UUID> ids);

    /**
     * Effective role codes for a user: the role, the assignment, and the user must all be ACTIVE
     * ({@code specs/10-P1-Identity-V1.5.md §12, §70}).
     */
    @Query("""
            select distinct r.code
            from Role r, UserRole ur, User u
            where ur.roleId = r.id
              and u.id = ur.userId
              and u.id = :userId
              and u.status = com.company.exam.common.enums.Status.ACTIVE
              and r.status = com.company.exam.common.enums.Status.ACTIVE
              and ur.status = com.company.exam.common.enums.Status.ACTIVE
            order by r.code
            """)
    List<String> findEffectiveRoleCodes(@Param("userId") UUID userId);
}
