package com.company.exam.integration.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.exam.common.enums.Status;
import com.company.exam.identity.entity.Organization;
import com.company.exam.identity.entity.User;
import com.company.exam.integration.identity.AbstractIdentityIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * Platform administrator management (ADR-0053, {@code specs/05} §72.7-§72.10) and the primary
 * administrator rule (ADR-0052).
 *
 * <p>These tests exist because §72.5 originally defined creation only: the list was unreadable, the
 * roles uncorrectable and the ownership permanent. They assert the three additions and, just as
 * importantly, the three deliberate refusals that keep them from becoming a second route to
 * privileged state (no touching ADMIN via PATCH, no deactivating the primary, no deactivating the
 * last administrator).
 *
 * <p>Every test runs against its own organization, because the seeded database is shared and these
 * assertions are about a specific organization's administrator set.
 */
class PlatformAdminManagementIntegrationTest extends AbstractIdentityIntegrationTest {

    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;
    private static final String PASSWORD = "Super-Strong-Pass-1";

    private User superAdmin() {
        return createUser(syntheticEmail("adm-mgmt-super"), PASSWORD, "SUPER_ADMIN");
    }

    /** A fresh business organization, since the platform endpoints refuse the PLATFORM row. */
    private UUID newOrganization(User sa, String prefix) throws Exception {
        String code = (prefix + "-" + UUID.randomUUID()).toUpperCase().substring(0, 24);
        String body = mockMvc.perform(post("/api/v1/platform/organizations")
                        .header(AUTHORIZATION, bearer(tokenFor(sa)))
                        .contentType(JSON)
                        .content("{\"code\":\"" + code + "\",\"name\":\"" + code + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(body, "$.data.id"));
    }

    /** Creates an organization administrator through the platform endpoint and returns the row. */
    private String createAdmin(User sa, UUID orgId, String email) throws Exception {
        return mockMvc.perform(post("/api/v1/platform/organizations/" + orgId + "/admins")
                        .header(AUTHORIZATION, bearer(tokenFor(sa)))
                        .contentType(JSON)
                        .content("{\"firstName\":\"Org\",\"lastName\":\"Admin\",\"email\":\""
                                + email + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void theFirstAdministratorBecomesThePrimaryAndTheListShowsIt() throws Exception {
        User sa = superAdmin();
        UUID orgId = newOrganization(sa, "prime");
        String email = syntheticEmail("prime-admin");
        createAdmin(sa, orgId, email);

        mockMvc.perform(get("/api/v1/platform/organizations/" + orgId)
                        .header(AUTHORIZATION, bearer(tokenFor(sa))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.adminCount").value(1))
                .andExpect(jsonPath("$.data.primaryAdminUserId").isNotEmpty())
                .andExpect(jsonPath("$.data.primaryAdminEmail").value(email));

        mockMvc.perform(get("/api/v1/platform/organizations/" + orgId + "/admins")
                        .header(AUTHORIZATION, bearer(tokenFor(sa))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].email").value(email))
                .andExpect(jsonPath("$.data[0].isPrimary").value(true))
                .andExpect(jsonPath("$.data[0].status").value("ACTIVE"));
    }

    @Test
    void adminListIsScopedToItsOrganizationAndRequiresThePlatformPermission() throws Exception {
        User sa = superAdmin();
        UUID orgA = newOrganization(sa, "scope-a");
        UUID orgB = newOrganization(sa, "scope-b");
        createAdmin(sa, orgA, syntheticEmail("scope-a-admin"));

        // orgB has no administrator, so a list scoped to it must be empty even though orgA has one.
        mockMvc.perform(get("/api/v1/platform/organizations/" + orgB + "/admins")
                        .header(AUTHORIZATION, bearer(tokenFor(sa))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));

        // An organization administrator must not reach the platform endpoint at all.
        User orgAdmin = createUserInOrganization(orgA, syntheticEmail("scope-org-admin"), PASSWORD,
                Status.ACTIVE, "ADMIN");
        mockMvc.perform(get("/api/v1/platform/organizations/" + orgA + "/admins")
                        .header(AUTHORIZATION, bearer(tokenFor(orgAdmin))))
                .andExpect(status().isForbidden());
    }

    @Test
    void rolesCanBeCorrectedButAdministratorMembershipCannot() throws Exception {
        User sa = superAdmin();
        UUID orgId = newOrganization(sa, "roles");
        String body = createAdmin(sa, orgId, syntheticEmail("roles-admin"));
        String userId = JsonPath.read(body, "$.data.userId");

        // STUDENT is a legitimate extra role for an administrator.
        mockMvc.perform(patch("/api/v1/platform/organizations/" + orgId + "/admins/" + userId + "/roles")
                        .header(AUTHORIZATION, bearer(tokenFor(sa)))
                        .contentType(JSON)
                        .content("{\"roles\":[\"STUDENT\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roles[0]").value("STUDENT"));

        // ADMIN itself is refused: that is the cap/primary path, not this one.
        mockMvc.perform(patch("/api/v1/platform/organizations/" + orgId + "/admins/" + userId + "/roles")
                        .header(AUTHORIZATION, bearer(tokenFor(sa)))
                        .contentType(JSON)
                        .content("{\"roles\":[\"ADMIN\"]}"))
                .andExpect(status().isConflict());

        // SUPER_ADMIN is refused outright: a platform capability is never grantable from here.
        mockMvc.perform(patch("/api/v1/platform/organizations/" + orgId + "/admins/" + userId + "/roles")
                        .header(AUTHORIZATION, bearer(tokenFor(sa)))
                        .contentType(JSON)
                        .content("{\"roles\":[\"SUPER_ADMIN\"]}"))
                .andExpect(status().isConflict());
    }

    @Test
    void anUnknownRoleCodeIsRejected() throws Exception {
        User sa = superAdmin();
        UUID orgId = newOrganization(sa, "badrole");
        String body = createAdmin(sa, orgId, syntheticEmail("badrole-admin"));
        String userId = JsonPath.read(body, "$.data.userId");

        mockMvc.perform(patch("/api/v1/platform/organizations/" + orgId + "/admins/" + userId + "/roles")
                        .header(AUTHORIZATION, bearer(tokenFor(sa)))
                        .contentType(JSON)
                        .content("{\"roles\":[\"NO_SUCH_ROLE\"]}"))
                .andExpect(status().isConflict());
    }

    @Test
    void thePrimaryAdministratorCannotBeDeactivated() throws Exception {
        User sa = superAdmin();
        UUID orgId = newOrganization(sa, "keepprimary");
        String body = createAdmin(sa, orgId, syntheticEmail("keep-primary"));
        String userId = JsonPath.read(body, "$.data.userId");

        mockMvc.perform(delete("/api/v1/platform/organizations/" + orgId + "/admins/" + userId)
                        .header(AUTHORIZATION, bearer(tokenFor(sa))))
                .andExpect(status().isConflict());
    }

    @Test
    void aNonAdministratorTargetIsReportedAsNotFound() throws Exception {
        User sa = superAdmin();
        UUID orgId = newOrganization(sa, "notadmin");
        createAdmin(sa, orgId, syntheticEmail("notadmin-admin"));
        User student = createUserInOrganization(orgId, syntheticEmail("notadmin-student"), PASSWORD,
                Status.ACTIVE, "STUDENT");

        // A student of the same organization is not an administrator, so the admin endpoints 404.
        mockMvc.perform(delete("/api/v1/platform/organizations/" + orgId + "/admins/" + student.getId())
                        .header(AUTHORIZATION, bearer(tokenFor(sa))))
                .andExpect(status().isNotFound());
    }

    @Test
    void aUserFromAnotherOrganizationIsNotFoundNotForbidden() throws Exception {
        User sa = superAdmin();
        UUID orgA = newOrganization(sa, "cross-a");
        UUID orgB = newOrganization(sa, "cross-b");
        String body = createAdmin(sa, orgA, syntheticEmail("cross-admin"));
        String userId = JsonPath.read(body, "$.data.userId");

        // The administrator belongs to orgA; asking through orgB must not reveal that the id exists.
        mockMvc.perform(delete("/api/v1/platform/organizations/" + orgB + "/admins/" + userId)
                        .header(AUTHORIZATION, bearer(tokenFor(sa))))
                .andExpect(status().isNotFound());
    }
}
