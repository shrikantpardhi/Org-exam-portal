package com.company.exam.integration.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.exam.common.enums.Status;
import com.company.exam.identity.entity.Permission;
import com.company.exam.identity.entity.Role;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * D-12: the SUPER_ADMIN permission set is pinned.
 *
 * <p>ADR-0032 makes the installation owner's capability a contract statement, and `V32` adds the two
 * catalogue read permissions its seeds screens require. Until this test existed, **no** test in the
 * suite referenced a `PLATFORM_*` code: a migration, a seed, or a manual grant could change the
 * owner's capability with nothing failing. Every other defect in the hardening mission is a question
 * about this set, so the set itself has to be checkable.
 *
 * <p>The expected set is written out literally rather than derived, because a derived expectation
 * would move with the thing it is meant to pin.
 *
 * <p><strong>Edited by Task 7 on Gate A approval (answer A2/A2b).</strong> The pinned set moved from
 * 10 codes to 16: the installation owner was granted role authoring (`ROLE_CREATE`,
 * `ROLE_UPDATE`, `ROLE_PERMISSION_ASSIGN`) and the reserved-set bypass
 * (`UNBOUNDED_PERMISSION_ASSIGN`), plus the two new platform capabilities
 * (`PLATFORM_ADMIN_VIEW`, `PLATFORM_ADMIN_RECOVER`, `V34`). This is the forcing function
 * working as designed: widening the owner's capability cannot happen silently, it has to be written
 * here, in a file whose whole purpose is to be read by a reviewer.
 */
class SuperAdminPermissionCatalogueTest extends AbstractIdentityIntegrationTest {

    /**
     * `V29` grants the eight platform codes; `V32` adds the two catalogue reads (D-01); `V34` adds
     * role authoring, the reserved-set bypass, and the recovery/view capabilities (Gate A A2/A2b/A4);
     * `V38` adds `LOOKUP_VIEW` so the read-only lookups seeds screen stops 403-ing (2026-10-26).
     *
     * <p>The lookups grant is the same D-01 defect one screen later: the UI gated /platform/seeds/lookups
     * on PLATFORM_SYSTEM_VIEW while the API behind it required LOOKUP_VIEW. Write permissions for
     * lookups are deliberately still absent — the seeds screen is read-only.
     */
    private static final Set<String> EXPECTED = Set.of(
            "PLATFORM_ORGANIZATION_VIEW", "PLATFORM_ORGANIZATION_CREATE",
            "PLATFORM_ORGANIZATION_UPDATE", "PLATFORM_ADMIN_CREATE",
            "PLATFORM_SYSTEM_VIEW", "PLATFORM_PROVIDER_VIEW",
            "PLATFORM_PROVIDER_UPDATE", "PLATFORM_STATUS_VIEW",
            "PLATFORM_ADMIN_VIEW", "PLATFORM_ADMIN_RECOVER",
            "ROLE_VIEW", "PERMISSION_VIEW", "LOOKUP_VIEW",
            "ROLE_CREATE", "ROLE_UPDATE", "ROLE_PERMISSION_ASSIGN",
            "UNBOUNDED_PERMISSION_ASSIGN");

    private Set<String> superAdminPermissions() {
        Role superAdmin = role("SUPER_ADMIN");
        return permissionRepository.findEffectiveByRoleId(superAdmin.getId()).stream()
                .map(Permission::getCode)
                .collect(Collectors.toSet());
    }

    @Test
    void theSuperAdminPermissionSetIsExactlyThePinnedCatalogue() {
        assertThat(superAdminPermissions()).containsExactlyInAnyOrderElementsOf(EXPECTED);
    }

    @Test
    void everyPlatformPermissionIsHeldBySuperAdmin() {
        // The prefix rule, not a list: a future `PLATFORM_*` code that is seeded but not granted to
        // the installation owner fails here. This is the assertion that would have caught D-01's
        // mirror image (a platform permission nobody holds).
        List<String> platformCodes = permissionRepository.findAll().stream()
                .map(Permission::getCode)
                .filter(code -> code.startsWith("PLATFORM_"))
                .toList();

        assertThat(platformCodes).hasSize(10);
        assertThat(superAdminPermissions()).containsAll(platformCodes);
    }

    @Test
    void noOtherSeededRoleHoldsAPlatformPermission() {
        // ADR-0032: the platform catalogue is granted to SUPER_ADMIN only. D-03 was this rule's
        // absence, so it is pinned from the data side as well as from the API side.
        //
        // Scoped to the seeded catalogue on purpose: these integration tests share one database and
        // do not roll back, so a sibling test that legitimately grants a PLATFORM_* code to a
        // disposable role (the ADR-0039 bypass test) must not make this assertion order-dependent.
        List<Role> roles = roleRepository.findAllByCodeIn(List.of("ADMIN", "REVIEWER", "STAFF", "STUDENT",
                "SUPPORT", "FINANCE", "SUPER_ADMIN"));
        for (Role role : roles) {
            if ("SUPER_ADMIN".equals(role.getCode())) {
                continue;
            }
            Set<String> codes = permissionRepository.findEffectiveByRoleId(role.getId()).stream()
                    .map(Permission::getCode)
                    .collect(Collectors.toSet());
            assertThat(codes)
                    .as("role %s must not hold a platform permission", role.getCode())
                    .noneMatch(code -> code.startsWith("PLATFORM_"));
        }
    }

    @Test
    void superAdminAuthorsRolesButHoldsNoOrganizationUserSurface() {
        // Gate A answer A2 granted role authoring. What the decision did NOT grant is the /admin/*
        // operational surface: SUPER_ADMIN's user reach is the read-only /platform/users list, so it
        // must not be able to create, update, or archive another organization's users. That boundary
        // is the whole of requirement R2 for the platform tier.
        assertThat(superAdminPermissions()).contains(
                "ROLE_CREATE", "ROLE_UPDATE", "ROLE_PERMISSION_ASSIGN", "UNBOUNDED_PERMISSION_ASSIGN");
        assertThat(superAdminPermissions()).doesNotContain(
                "USER_VIEW", "USER_UPDATE", "USER_CREATE", "USER_ARCHIVE",
                "AUDIT_LOG_VIEW", "ANALYTICS_VIEW", "SYSTEM_HEALTH_VIEW",
                "ADMIN_ROLE_ASSIGN");
    }

    @Test
    void theSuperAdminRoleIsActiveAndUnique() {
        // ADR-0032: permanent, unique, non-archivable. The uniqueness of the *user* is covered by
        // SuperAdminImmutabilityIntegrationTest; this pins the role row itself.
        List<Role> matches = roleRepository.findAll().stream()
                .filter(role -> "SUPER_ADMIN".equals(role.getCode()))
                .toList();
        assertThat(matches).hasSize(1);
        assertThat(matches.get(0).getStatus()).isEqualTo(Status.ACTIVE);
    }
}
