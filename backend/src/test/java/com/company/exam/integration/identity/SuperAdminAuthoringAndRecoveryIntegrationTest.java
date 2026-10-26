package com.company.exam.integration.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.exam.common.enums.Status;
import com.company.exam.identity.entity.Organization;
import com.company.exam.identity.entity.Permission;
import com.company.exam.identity.entity.Role;
import com.company.exam.identity.entity.User;
import com.company.exam.identity.entity.UserRole;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * Task 7 (D-06) and Task 8 (D-08 Rule A), ADR-0042 and ADR-0044, on Gate A approval.
 *
 * <p>Three things are asserted here that the mission treats as one decision:
 *
 * <ol>
 *   <li><b>A2</b> — {@code SUPER_ADMIN} now authors roles: {@code POST /roles} and
 *       {@code PUT /roles/{id}/permissions} succeed, using the create-then-assign sequence rather
 *       than a permissions field on creation (the DTO is unchanged, so no contract amendment).
 *   <li><b>A1 / A4</b> — the recovery endpoints and the cross-organization user list exist, are
 *       SUPER_ADMIN-only, and are organization-scoped.
 *   <li><b>A3</b> — a role carrying a permission the assigner does not hold cannot be assigned
 *       ({@code ROLE_ESCALATION_DETECTED}), closing D-08's Rule A.
 * </ol>
 *
 * <p>Scoped to seeded rows throughout: these tests share one non-rollback database, so a global
 * count would be order-dependent.
 */
class SuperAdminAuthoringAndRecoveryIntegrationTest extends AbstractIdentityIntegrationTest {

    private static final String PASSWORD = "Super-Strong-Pass-1";

    private String superAdminBearer() {
        return bearer(tokenFor(createUser(syntheticEmail("super-owner"), PASSWORD, "SUPER_ADMIN")));
    }

    private String adminBearer() {
        return bearer(tokenFor(createUser(syntheticEmail("admin-recover"), PASSWORD, "ADMIN")));
    }

    private String permissionIdByCode(String code) {
        return permissionRepository.findAll().stream()
                .filter(permission -> code.equals(permission.getCode()))
                .map(permission -> permission.getId().toString())
                .findFirst()
                .orElseThrow();
    }

    private List<UUID> permissionIdsOf(String roleCode) {
        return permissionRepository.findEffectiveByRoleId(role(roleCode).getId()).stream()
                .map(Permission::getId)
                .toList();
    }

    // ---------------------------------------------------------------- A2: authoring

    @Test
    void aSuperAdminCanCreateARoleAndThenAssignPermissionsToIt() throws Exception {
        String bearer = superAdminBearer();
        String code = "T7_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        String body = mockMvc.perform(post("/api/v1/roles")
                        .header(AUTHORIZATION, bearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + code + "\",\"name\":\"Task Seven Role\","
                                + "\"description\":\"created by the installation owner\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String roleId = com.jayway.jsonpath.JsonPath.read(body, "$.data.id");
        assertThat(roleId).isNotBlank();

        // Step two of the approved mechanism: create, then assign. The creation DTO has no
        // permissions field, so this is also the proof that no contract amendment was needed.
        mockMvc.perform(put("/api/v1/roles/" + roleId + "/permissions")
                        .header(AUTHORIZATION, bearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permissionIds\":[\"" + permissionIdByCode("ROLE_VIEW") + "\"]}"))
                .andExpect(status().isOk());

        List<String> assigned = permissionRepository.findEffectiveByRoleId(UUID.fromString(roleId)).stream()
                .map(Permission::getCode).toList();
        assertThat(assigned).containsExactly("ROLE_VIEW");
    }

    @Test
    void aSuperAdminCanAssignAReservedPlatformPermissionBecauseItHoldsTheBypass() throws Exception {
        // Gate A answer A2b granted UNBOUNDED_PERMISSION_ASSIGN. Without it the new authoring
        // capability would be unable to build any role carrying a PLATFORM_* code, which is the
        // whole point of the reserved set being bypassable by the installation owner.
        String bearer = superAdminBearer();
        String code = "T7B_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        String body = mockMvc.perform(post("/api/v1/roles")
                        .header(AUTHORIZATION, bearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + code + "\",\"name\":\"Platform Role\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String roleId = com.jayway.jsonpath.JsonPath.read(body, "$.data.id");

        mockMvc.perform(put("/api/v1/roles/" + roleId + "/permissions")
                        .header(AUTHORIZATION, bearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permissionIds\":[\""
                                + permissionIdByCode("PLATFORM_ORGANIZATION_VIEW") + "\"]}"))
                .andExpect(status().isOk());
    }

    @Test
    void anAdminStillCannotPlaceAPlatformPermissionEvenThoughSuperAdminCan() throws Exception {
        // The bypass is a capability, not a relaxation of D-03. The reserved set must still refuse
        // the organization tier, otherwise Gate A's answer would have re-opened the defect.
        mockMvc.perform(put("/api/v1/roles/" + role("STAFF").getId() + "/permissions")
                        .header(AUTHORIZATION, adminBearer()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permissionIds\":[\""
                                + permissionIdByCode("PLATFORM_ORGANIZATION_VIEW") + "\"]}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errors[0].code").value("RESERVED_PERMISSION_DENIED"));
    }

    // ---------------------------------------------------------------- A4: the user list

    @Test
    void aSuperAdminCanListUsersAcrossOrganizations() throws Exception {
        mockMvc.perform(get("/api/v1/platform/users?size=5").header(AUTHORIZATION, superAdminBearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].organizationCode").exists());
    }

    @Test
    void theCrossOrganizationUserListIsRefusedToAnOrganizationAdmin() throws Exception {
        // D-10's lesson: a list that spans organizations is a platform capability. An ADMIN
        // reaching it would be the same defect at a different URL.
        mockMvc.perform(get("/api/v1/platform/users").header(AUTHORIZATION, adminBearer()))
                .andExpect(status().isForbidden());
    }

    @Test
    void theCrossOrganizationUserListIncludesThePlatformOrganization() throws Exception {
        // ADR-0033 excludes PLATFORM from every *business* list. This is not a business list: the
        // installation owner is a user the platform tier must be able to see. Asserted because the
        // opposite choice (filtering PLATFORM out) is the one a later reader would assume.
        //
        // The fixture seeds the PLATFORM organization but no user in it, so one is created here.
        // Without that this test would pass for the wrong reason — an empty PLATFORM org would make
        // "included" indistinguishable from "filtered out".
        //
        // Deliberately roleless: ADR-0032 makes the SUPER_ADMIN account unique, and
        // SuperAdminBootstrapProvisioningIntegrationTest asserts exactly one active SUPER_ADMIN in
        // PLATFORM. Giving this fixture a role would break that invariant from a neighbouring test.
        UUID platformOrgId = organizationRepository.findAll().stream()
                .filter(organization -> "PLATFORM".equals(organization.getCode()))
                .map(Organization::getId)
                .findFirst()
                .orElseThrow();
        createUserInOrganization(platformOrgId, syntheticEmail("platform-resident"), PASSWORD, Status.ACTIVE);

        String body = mockMvc.perform(get("/api/v1/platform/users?organizationId=" + platformOrgId)
                        .header(AUTHORIZATION, superAdminBearer()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        List<String> ids = com.jayway.jsonpath.JsonPath.read(body, "$.data[*].organizationId");
        assertThat(ids).isNotEmpty().containsOnly(platformOrgId.toString());
    }

    @Test
    void theUserListFiltersByOrganization() throws Exception {
        UUID organizationId = defaultOrganizationId();
        String body = mockMvc.perform(get("/api/v1/platform/users?size=5&organizationId=" + organizationId)
                        .header(AUTHORIZATION, superAdminBearer()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<String> ids = com.jayway.jsonpath.JsonPath.read(body, "$.data[*].organizationId");
        assertThat(ids).isNotEmpty().containsOnly(organizationId.toString());
    }

    // ---------------------------------------------------------------- A1: recovery

    @Test
    void aSuperAdminCanResetALockedOutAdminsPassword() throws Exception {
        User target = createUser(syntheticEmail("locked-out-admin"), PASSWORD, "ADMIN");
        UUID organizationId = target.getOrganizationId();

        mockMvc.perform(post("/api/v1/platform/organizations/" + organizationId
                        + "/admins/" + target.getId() + "/reset-password")
                        .header(AUTHORIZATION, superAdminBearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(target.getId().toString()))
                .andExpect(jsonPath("$.data.action").value("RESET_PASSWORD"));

        // The account is forced to choose a new password on next login (ADR-0034), and never
        // receives one from this endpoint.
        User reloaded = userRepository.findById(target.getId()).orElseThrow();
        assertThat(reloaded.isMustChangePassword()).isTrue();
    }

    @Test
    void aResetRevokesTheTargetsActiveSessions() throws Exception {
        User target = createUser(syntheticEmail("compromised-admin"), PASSWORD, "ADMIN");
        UUID organizationId = target.getOrganizationId();

        // Log the target in so there is a session to revoke.
        String token = tokenFor(target);
        assertThat(token).isNotBlank();

        mockMvc.perform(post("/api/v1/platform/organizations/" + organizationId
                        + "/admins/" + target.getId() + "/unlock-sessions")
                        .header(AUTHORIZATION, superAdminBearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.action").value("UNLOCK_SESSIONS"))
                .andExpect(jsonPath("$.data.revokedSessions").value(org.hamcrest.Matchers.greaterThan(0)));

        // Containment: the credential held by the attacker no longer works.
        mockMvc.perform(get("/api/v1/users/me").header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void recoveryRefusesAUserThatIsNotAnAdministrator() throws Exception {
        User target = createUser(syntheticEmail("not-an-admin"), PASSWORD, "STUDENT");

        mockMvc.perform(post("/api/v1/platform/organizations/" + target.getOrganizationId()
                        + "/admins/" + target.getId() + "/reset-password")
                        .header(AUTHORIZATION, superAdminBearer()))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void recoveryCannotReachAUserInAnotherOrganization() throws Exception {
        // The organization is in the path and is checked against the user's own organization_id,
        // so a platform operator cannot recover an administrator by guessing a user id.
        User target = createUser(syntheticEmail("cross-org-admin"), PASSWORD, "ADMIN");
        Organization other = organizationRepository.findAll().stream()
                .filter(organization -> !organization.getId().equals(target.getOrganizationId()))
                .filter(organization -> !"PLATFORM".equals(organization.getCode()))
                .findFirst()
                .orElse(null);

        if (other == null) {
            // One business organization on this fixture: the mismatch case collapses into the
            // PLATFORM refusal, which is asserted below.
            String platformOrgId = organizationRepository.findAll().stream()
                    .filter(organization -> "PLATFORM".equals(organization.getCode()))
                    .map(organization -> organization.getId().toString()).findFirst().orElseThrow();
            mockMvc.perform(post("/api/v1/platform/organizations/" + platformOrgId
                            + "/admins/" + target.getId() + "/reset-password")
                            .header(AUTHORIZATION, superAdminBearer()))
                    .andExpect(status().isNotFound());
            return;
        }

        mockMvc.perform(post("/api/v1/platform/organizations/" + other.getId()
                        + "/admins/" + target.getId() + "/reset-password")
                        .header(AUTHORIZATION, superAdminBearer()))
                .andExpect(status().isNotFound());
    }

    @Test
    void recoveryOfTheSuperAdminAccountIsRefused() throws Exception {
        // The installation owner is refused with SUPER_ADMIN_IMMUTABLE (409), not with the generic
        // "not an administrator" (422) and not with a 404. The three refusals are ordered so the
        // specific one wins: this account is protected by a different invariant, and reporting it as
        // a missing resource would send an operator looking for a data problem that does not exist.
        User superAdmin = createUser(syntheticEmail("super-target"), PASSWORD, "SUPER_ADMIN");

        mockMvc.perform(post("/api/v1/platform/organizations/" + superAdmin.getOrganizationId()
                        + "/admins/" + superAdmin.getId() + "/reset-password")
                        .header(AUTHORIZATION, superAdminBearer()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].code").value("SUPER_ADMIN_IMMUTABLE"));
    }

    @Test
    void recoveryIsRefusedToAnOrganizationAdmin() throws Exception {
        User target = createUser(syntheticEmail("recover-target"), PASSWORD, "ADMIN");

        mockMvc.perform(post("/api/v1/platform/organizations/" + target.getOrganizationId()
                        + "/admins/" + target.getId() + "/reset-password")
                        .header(AUTHORIZATION, adminBearer()))
                .andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------- A3: Rule A

    @Test
    void aRoleCarryingAPermissionTheAssignerLacksCannotBeAssigned() throws Exception {
        // D-08 Rule A. Bounded delegation stops an ADMIN from *granting* ADMIN_ROLE_ASSIGN; this
        // stops the second administrator from *assigning* a role that already carries it.
        String adminBearer = adminBearer();

        // A role loaded with a code the ADMIN does not hold, placed by the SUPER_ADMIN who does.
        String code = "T8_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String created = mockMvc.perform(post("/api/v1/roles")
                        .header(AUTHORIZATION, superAdminBearer()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + code + "\",\"name\":\"Escalated Role\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String roleId = com.jayway.jsonpath.JsonPath.read(created, "$.data.id");

        mockMvc.perform(put("/api/v1/roles/" + roleId + "/permissions")
                        .header(AUTHORIZATION, superAdminBearer()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permissionIds\":[\"" + permissionIdByCode("ADMIN_ROLE_ASSIGN") + "\"]}"))
                .andExpect(status().isOk());

        // The ADMIN assigns that role to a fresh user.
        String email = syntheticEmail("escalation-target");
        mockMvc.perform(post("/api/v1/users")
                        .header(AUTHORIZATION, adminBearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Escalation\",\"lastName\":\"Target\","
                                + "\"email\":\"" + email + "\",\"roles\":[\"" + roleId + "\"]}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errors[0].code").value("ROLE_ESCALATION_DETECTED"));
    }

    @Test
    void anAdminCanStillAssignAnOrdinaryRole() throws Exception {
        // The guard must not become a blanket refusal: ordinary user administration is unchanged.
        String email = syntheticEmail("ordinary-assign");
        mockMvc.perform(post("/api/v1/users")
                        .header(AUTHORIZATION, adminBearer()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Ordinary\",\"lastName\":\"User\","
                                + "\"email\":\"" + email + "\",\"roles\":[\""
                                + role("STUDENT").getId() + "\"]}"))
                .andExpect(status().isCreated());
    }
}
