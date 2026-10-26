package com.company.exam.identity.service;

import com.company.exam.administration.api.BusinessRuleResolver;
import com.company.exam.identity.IdentityConstants;
import com.company.exam.identity.entity.Permission;
import com.company.exam.identity.exception.PermissionNotHeldException;
import com.company.exam.identity.exception.RoleEscalationException;
import com.company.exam.identity.exception.ReservedPermissionException;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * The reserved-permission policy ({@code 20-P11-Administration-V1.5.md} §17, ADR-0039, D-03).
 *
 * <p>The reserved set is declared as data — the {@code RESERVED_PERMISSIONS} business rule — not
 * as a Java list ({@code ADR-0023} decision 6, "rules are data, never code"). A future platform
 * permission matching a reserved prefix is therefore covered without a code change and without a
 * list to keep in sync.
 *
 * <p>Fail-closed (G-10): when the rule is missing, non-ACTIVE, or unreadable, <em>every</em>
 * permission is treated as reserved and no caller holds the bypass. The endpoint then refuses all
 * permission assignment rather than allowing all of it, which is the only safe direction for a
 * security control.
 */
@Service
public class RolePermissionPolicy {

    private static final Logger log = LoggerFactory.getLogger(RolePermissionPolicy.class);

    /** The business rule that declares the reserved set ({@code V33}). */
    public static final String RESERVED_RULE = "RESERVED_PERMISSIONS";

    /** Used when the rule cannot be read, so a fail-closed refusal still names a real code. */
    static final String DEFAULT_BYPASS = "UNBOUNDED_PERMISSION_ASSIGN";

    /** A prefix that matches every code, used only to fail closed. */
    private static final String MATCH_EVERYTHING = "";

    private final BusinessRuleResolver ruleResolver;
    private final AuthorizationService authorizationService;
    private final com.company.exam.identity.repository.PermissionRepository permissionRepository;
    private final com.company.exam.identity.repository.RoleRepository roleRepository;

    public RolePermissionPolicy(BusinessRuleResolver ruleResolver, AuthorizationService authorizationService,
            com.company.exam.identity.repository.PermissionRepository permissionRepository,
            com.company.exam.identity.repository.RoleRepository roleRepository) {
        this.ruleResolver = ruleResolver;
        this.authorizationService = authorizationService;
        this.permissionRepository = permissionRepository;
        this.roleRepository = roleRepository;
    }

    /**
     * Refuses a permission replacement that would place a reserved code on a role, unless the caller
     * holds the bypass permission declared with the reserved set.
     *
     * @param desired the permissions the caller wants the role to end up with
     * @param actorId the acting user; {@code null} is an unattributable caller and is refused
     */
    public void checkReserved(Collection<Permission> desired, UUID actorId) {
        if (reservedCodes(desired).isEmpty()) {
            return;
        }
        if (isBypassHeld(actorId)) {
            return;
        }
        throw new ReservedPermissionException();
    }

    /** The subset of {@code permissions} whose codes are reserved, in input order. */
    public Set<String> reservedCodes(Collection<Permission> permissions) {
        if (permissions == null || permissions.isEmpty()) {
            return Set.of();
        }
        ReservedSet set = reservedSet();
        Set<String> matches = new LinkedHashSet<>();
        for (Permission permission : permissions) {
            if (permission == null || permission.getCode() == null) {
                continue;
            }
            String code = permission.getCode().toUpperCase(Locale.ROOT);
            boolean reserved = set.codes().contains(code)
                    || set.prefixes().stream().anyMatch(code::startsWith);
            if (reserved) {
                matches.add(code);
            }
        }
        return matches;
    }

    /**
     * Refuses a permission replacement that would grant a code the caller does not itself hold
     * (bounded delegation, {@code 20-P11} §17, ADR-0040, D-04 and D-09).
     *
     * <p>This is the structural rule: {@code ROLE_PERMISSION_ASSIGN} must not be a god mode. Naming
     * the dangerous codes instead (which D-03 does, for the platform scope) would need a list that
     * every future permission has to be added to; this inverts the default, so a new permission is
     * safe without a list edit.
     *
     * <p>The bypass permission that satisfies the reserved set also satisfies this rule: a principal
     * with {@code UNBOUNDED_PERMISSION_ASSIGN} is, by definition, unbounded.
     */
    public void checkDelegation(Collection<Permission> desired, UUID actorId) {
        if (desired == null || desired.isEmpty()) {
            return;
        }
        if (isBypassHeld(actorId)) {
            return;
        }
        Set<String> held = Set.copyOf(authorizationService.effectivePermissions(actorId));
        boolean grantingSomethingUnheld = desired.stream()
                .map(Permission::getCode)
                .filter(java.util.Objects::nonNull)
                .map(code -> code.toUpperCase(Locale.ROOT))
                .anyMatch(code -> !held.contains(code));
        if (grantingSomethingUnheld) {
            throw new PermissionNotHeldException();
        }
    }

    /**
     * Refuses the <em>assignment</em> of a role whose effective permission set contains a code the
     * assigning caller does not hold (D-08 Rule A, ADR-0044).
     *
     * <p>Bounded delegation ({@link #checkDelegation}) stops a caller from <em>granting</em> a code it
     * lacks. It does not stop this sequence:
     *
     * <ol>
     *   <li>someone who legitimately holds {@code ADMIN_ROLE_ASSIGN} puts it on a role;
     *   <li>a second administrator, who does not hold it, assigns that role to a user.
     * </ol>
     *
     * <p>The escalation in step 2 is real even though the caller never named a permission: it moved
     * authority by naming a <em>role</em>. So the check is on the role's content, not on the request
     * body — which is why this uses a distinct code, {@code ROLE_ESCALATION_DETECTED}, rather than
     * {@code PERMISSION_NOT_HELD}: the request was well-formed and the role was legitimate; what is
     * refused is that <em>this</em> caller may not hand it out.
     *
     * @param roleIds the roles the caller wants to assign
     * @param actorId the acting user; {@code null} is refused unless the role is empty
     */
    public void checkAssignable(List<UUID> roleIds, UUID actorId) {
        if (roleIds == null || roleIds.isEmpty()) {
            return;
        }
        if (isBypassHeld(actorId)) {
            return;
        }
        Set<String> held = actorId == null ? Set.of()
                : Set.copyOf(authorizationService.effectivePermissions(actorId));
        for (UUID roleId : roleIds) {
            // ADMIN_ROLE_ASSIGN *is* the authority to hand out the ADMIN role. Without this
            // carve-out the rule would make that permission dead: ADMIN carries 144 codes, so only
            // an existing ADMIN could ever satisfy a full containment check — and the one-ADMIN-per
            // -organization cap (ADR-0032) means there is nobody else. The permission exists to name
            // "this principal may create administrators", so holding it satisfies the check for the
            // ADMIN role specifically, and only for that role.
            if (held.contains(IdentityConstants.ADMIN_ROLE_ASSIGN) && isAdminRole(roleId)) {
                continue;
            }
            boolean escalated = permissionRepository.findEffectiveByRoleId(roleId).stream()
                    .map(Permission::getCode)
                    .filter(java.util.Objects::nonNull)
                    .map(code -> code.toUpperCase(Locale.ROOT))
                    .anyMatch(code -> !held.contains(code));
            if (escalated) {
                throw new RoleEscalationException();
            }
        }
    }

    private boolean isAdminRole(UUID roleId) {
        return roleRepository.findByCodeAndOrganizationIdIsNull(IdentityConstants.ADMIN_ROLE_CODE)
                .map(role -> role.getId().equals(roleId))
                .orElse(false);
    }

    /** True when {@code actorId} holds the bypass permission declared with the reserved set. */
    public boolean isBypassHeld(UUID actorId) {
        return actorId != null && authorizationService.hasPermission(actorId, reservedSet().bypassPermission());
    }

    /**
     * Resolves the reserved set. A missing, non-ACTIVE, or unreadable rule yields the fail-closed
     * set: every permission is reserved, and the bypass is held by nobody.
     */
    ReservedSet reservedSet() {
        return ruleResolver.resolve(RESERVED_RULE)
                .map(this::toReservedSet)
                .orElseGet(() -> {
                    log.warn("Business rule {} is missing or not ACTIVE; failing closed and treating every "
                            + "permission as reserved", RESERVED_RULE);
                    return new ReservedSet(List.of(MATCH_EVERYTHING), Set.of(), DEFAULT_BYPASS);
                });
    }

    private ReservedSet toReservedSet(BusinessRuleResolver.ResolvedRule rule) {
        List<String> prefixes = upperList(rule.values().get("prefixes"));
        Set<String> codes = new LinkedHashSet<>(upperList(rule.values().get("codes")));
        String bypass = rule.string("bypassPermission")
                .filter(text -> !text.isBlank())
                .orElse(DEFAULT_BYPASS);
        if (prefixes.isEmpty() && codes.isEmpty()) {
            log.warn("Business rule {} declares no reserved code; failing closed", RESERVED_RULE);
            return new ReservedSet(List.of(MATCH_EVERYTHING), Set.of(), bypass);
        }
        return new ReservedSet(prefixes, codes, bypass);
    }

    private static List<String> upperList(Object value) {
        if (!(value instanceof Collection<?> items)) {
            return List.of();
        }
        return items.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .map(text -> text.toUpperCase(Locale.ROOT))
                .toList();
    }

    /** A data-only view of the reserved set; never executable ({@code 20-P11} §9.3). */
    record ReservedSet(List<String> prefixes, Set<String> codes, String bypassPermission) {
    }
}
