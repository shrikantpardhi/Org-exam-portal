package com.company.exam.integration.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.company.exam.common.enums.Status;
import com.company.exam.identity.entity.Organization;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * R1: the platform tier administers the whole installation, so it must see every role.
 *
 * <p>This class exists because the first version of the D-05 fix scoped <em>every</em> caller to its own
 * organization — including {@code SUPER_ADMIN}, which belongs to {@code PLATFORM}. Once a business
 * organization created a role, the platform tier could no longer see it. Before V35 all roles were
 * global, so this was a regression introduced by the fix, not a product decision.
 *
 * <p>It was caught by live verification, **not** by the test suite: the existing tests all ran with
 * only platform-wide roles present, where the buggy and correct behaviour are identical. That is the
 * lesson this class encodes — a scoping change needs a fixture with data on both sides of the
 * boundary.
 */
class PlatformRoleVisibilityIntegrationTest extends AbstractIdentityIntegrationTest {

    private static final String PASSWORD = "Correct-Horse-Battery-9";

    private Organization newOrganization() {
        return createOrganization("PLATVIS_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8),
                Status.ACTIVE);
    }

    /**
     * The system administrator. R1 is about holding the SUPER_ADMIN role, not about residing in the
     * PLATFORM organization: ADR-0032 permits exactly one SUPER_ADMIN account and the bootstrap check
     * is keyed on PLATFORM residence, so a second PLATFORM-resident SUPER_ADMIN would break that
     * invariant and SuperAdminBootstrapProvisioningIntegrationTest.
     */
    private String systemAdministrator() {
        return bearer(tokenFor(createUser(syntheticEmail("platvis-owner"), PASSWORD, "SUPER_ADMIN")));
    }

    private String adminOf(UUID organizationId) {
        return bearer(tokenFor(createUserInOrganization(organizationId,
                syntheticEmail("platvis-admin"), PASSWORD, Status.ACTIVE, "ADMIN")));
    }

    private String createRole(String bearer, String code) throws Exception {
        String body = mockMvc.perform(post("/api/v1/roles")
                        .header(AUTHORIZATION, bearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + code + "\",\"name\":\"Probe\"}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status()
                        .isCreated())
                .andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(body, "$.data.id");
    }

    @Test
    void thePlatformTierSeesARoleOwnedByAnotherOrganization() throws Exception {
        // The regression: without the platform bypass this list is missing the organization's role.
        Organization organization = newOrganization();
        String code = "PLATVIS_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        createRole(adminOf(organization.getId()), code);

        String body = mockMvc.perform(get("/api/v1/roles?size=500").header(AUTHORIZATION, systemAdministrator()))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<String> codes = com.jayway.jsonpath.JsonPath.read(body, "$.data[*].code");

        assertThat(codes).as("R1: SUPER_ADMIN must see every role").contains(code);
    }

    @Test
    void aBusinessOrganizationStillCannotSeeAnotherOrganizationsRole() throws Exception {
        // The platform bypass must not leak down to tenants, or D-05 would be reopened.
        Organization owner = newOrganization();
        Organization outsider = newOrganization();
        String code = "PLATVIS_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        createRole(adminOf(owner.getId()), code);

        String body = mockMvc.perform(get("/api/v1/roles?size=500")
                        .header(AUTHORIZATION, adminOf(outsider.getId())))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<String> codes = com.jayway.jsonpath.JsonPath.read(body, "$.data[*].code");

        assertThat(codes).doesNotContain(code);
    }
}