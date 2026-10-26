package com.company.exam.identity.service;

import com.company.exam.common.cache.CacheService;
import com.company.exam.common.exception.AuthorizationException;
import com.company.exam.identity.repository.PermissionRepository;
import com.company.exam.identity.repository.RoleRepository;
import com.company.exam.identity.security.IdentityPrincipal;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * The focused authorization decision service ({@code specs/10-P1-Identity-V1.5.md §69}).
 *
 * <p>It answers only permission questions. It does not own question validation, assessment rules,
 * payment logic, or evaluation. Effective permissions are resolved from PostgreSQL (a single join
 * query) and cached in Redis with a short TTL. A cache outage resolves from PostgreSQL and never
 * grants access ({@code §71-§74, §149}).
 */
@Service
public class AuthorizationService {

    private static final Logger log = LoggerFactory.getLogger(AuthorizationService.class);

    private static final String KEY_PREFIX = "authz:perms:";
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);

    private final PermissionRepository permissionRepository;
    private final RoleRepository roleRepository;
    private final CacheService cacheService;

    public AuthorizationService(PermissionRepository permissionRepository, RoleRepository roleRepository,
            CacheService cacheService) {
        this.permissionRepository = permissionRepository;
        this.roleRepository = roleRepository;
        this.cacheService = cacheService;
    }

    /** Permission check for a known user id. */
    public boolean hasPermission(UUID userId, String permissionCode) {
        if (userId == null || permissionCode == null) {
            return false;
        }
        return effectivePermissions(userId).contains(permissionCode);
    }

    /** SpEL-friendly check used by {@code @PreAuthorize("@authorizationService.hasPermission(authentication, 'X')")}. */
    public boolean hasPermission(Authentication authentication, String permissionCode) {
        return currentUserId(authentication).map(userId -> hasPermission(userId, permissionCode)).orElse(false);
    }

    /** SpEL-friendly role check used by {@code @PreAuthorize("@authorizationService.hasRole(authentication, 'ADMIN')")}. */
    public boolean hasRole(Authentication authentication, String roleCode) {
        return currentUserId(authentication)
                .map(userId -> roleRepository.findEffectiveRoleCodes(userId).contains(roleCode))
                .orElse(false);
    }

    /** Throws 403 when the user does not hold the permission. */
    public void requirePermission(UUID userId, String permissionCode) {
        if (!hasPermission(userId, permissionCode)) {
            throw new AuthorizationException("Access denied");
        }
    }

    /**
     * Effective, distinct permission codes for a user. PostgreSQL is authoritative; Redis is a
     * short-lived optimization only.
     */
    public List<String> effectivePermissions(UUID userId) {
        String key = KEY_PREFIX + userId;
        Optional<CachedPermissions> cached = cacheService.get(key, CachedPermissions.class);
        if (cached.isPresent()) {
            return cached.get().permissionCodes();
        }
        List<String> resolved = permissionRepository.findEffectivePermissionCodes(userId);
        cacheService.put(key, new CachedPermissions(resolved), CACHE_TTL);
        return resolved;
    }

    public Optional<UUID> currentUserId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof IdentityPrincipal identityPrincipal) {
            return Optional.of(identityPrincipal.userId());
        }
        return Optional.empty();
    }

    public Optional<IdentityPrincipal> currentPrincipal(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof IdentityPrincipal principal) {
            return Optional.of(principal);
        }
        return Optional.empty();
    }

    /** Evicts the cached permissions for a user. Called after a permission-affecting commit. */
    public void evict(UUID userId) {
        if (userId != null) {
            cacheService.evict(KEY_PREFIX + userId);
        }
    }

    /**
     * Evicts only after the surrounding transaction commits, so a failed transaction never
     * invalidates a good cache entry ({@code §72}).
     */
    public void evictAfterCommit(UUID userId) {
        if (userId == null) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    evict(userId);
                }
            });
        } else {
            log.debug("No active transaction; evicting authorization cache immediately for userId={}", userId);
            evict(userId);
        }
    }

    /** Cache envelope so {@link CacheService} can deserialize a typed value. */
    public record CachedPermissions(List<String> permissionCodes) {
    }
}
