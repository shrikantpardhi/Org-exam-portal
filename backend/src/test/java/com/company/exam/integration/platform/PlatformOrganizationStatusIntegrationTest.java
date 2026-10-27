package com.company.exam.integration.platform;

import static org.assertj.core.api.Assertions.assertThat;
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
 * Changing an organization's status (ADR-0055, {@code specs/05} §72.16).
 *
 * <p>The assertion that matters here is not "the field changed" — it is that a status change has the
 * consequence the UI promises. {@code AuthenticationService} refuses a login whose organization is not
 * {@code ACTIVE}, so {@code INACTIVE} must genuinely suspend the tenant. A test that only re-read the
 * detail endpoint would pass even if the lever were purely cosmetic.
 */
class PlatformOrganizationStatusIntegrationTest extends AbstractIdentityIntegrationTest {

    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;
    private static final String PASSWORD = "Super-Strong-Pass-1";

    private User superAdmin() {
        return createUser(syntheticEmail("status-super"), PASSWORD, "SUPER_ADMIN");
    }

    private UUID newOrganization(User sa) throws Exception {
        String code = "STS-" + UUID.randomUUID().toString().toUpperCase().substring(0, 16);
        String body = mockMvc.perform(post("/api/v1/platform/organizations")
                        .header(AUTHORIZATION, bearer(tokenFor(sa)))
                        .contentType(JSON)
                        .content("{\"code\":\"" + code + "\",\"name\":\"" + code + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(body, "$.data.id"));
    }

    private void setStatus(User sa, UUID orgId, String value, int expectedStatus) throws Exception {
        mockMvc.perform(patch("/api/v1/platform/organizations/" + orgId + "/status")
                        .header(AUTHORIZATION, bearer(tokenFor(sa)))
                        .contentType(JSON)
                        .content("{\"status\":\"" + value + "\"}"))
                .andExpect(status().is(expectedStatus));
    }

    @Test
    void anOrganizationCanBeSuspendedAndReinstated() throws Exception {
        User sa = superAdmin();
        UUID orgId = newOrganization(sa);

        setStatus(sa, orgId, "INACTIVE", 200);
        mockMvc.perform(get("/api/v1/platform/organizations/" + orgId)
                        .header(AUTHORIZATION, bearer(tokenFor(sa))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("INACTIVE"));

        setStatus(sa, orgId, "ACTIVE", 200);
        mockMvc.perform(get("/api/v1/platform/organizations/" + orgId)
                        .header(AUTHORIZATION, bearer(tokenFor(sa))))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    void suspensionBlocksLoginForEveryUserOfTheOrganization() throws Exception {
        User sa = superAdmin();
        UUID orgId = newOrganization(sa);

        String email = syntheticEmail("status-member");
        createUserInOrganization(orgId, email, PASSWORD, Status.ACTIVE, "STUDENT");

        // The user can sign in while the organization is active.
        mockMvc.perform(post("/api/v1/auth/login").contentType(JSON)
                        .content("{\"username\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk());

        setStatus(sa, orgId, "INACTIVE", 200);

        // ... and cannot once it is suspended. This is what makes the control a real lever rather than
        // a label, and it is the consequence the confirmation dialog warns about.
        mockMvc.perform(post("/api/v1/auth/login").contentType(JSON)
                        .content("{\"username\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isUnauthorized());

        // Reinstating the organization restores access without touching the user.
        setStatus(sa, orgId, "ACTIVE", 200);
        mockMvc.perform(post("/api/v1/auth/login").contentType(JSON)
                        .content("{\"username\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void settingTheCurrentStatusIsAnIdempotentNoOp() throws Exception {
        User sa = superAdmin();
        UUID orgId = newOrganization(sa);
        long before = auditLogRepository.count();

        setStatus(sa, orgId, "ACTIVE", 200);

        // No audit row: reporting a change that did not happen would put a false entry in the trail,
        // which is the one thing an audit log must never do.
        assertThat(auditLogRepository.count()).as("an unchanged status must not be audited").isEqualTo(before);
    }

    @Test
    void thePlatformTenantIsRefused() throws Exception {
        User sa = superAdmin();
        Organization platform = organizationRepository.findByCode("PLATFORM").orElseThrow();

        // Suspending the installation's own tenant would lock out every operator, including the
        // caller — and PLATFORM is not a business organization in the first place.
        setStatus(sa, platform.getId(), "INACTIVE", 404);
        Organization unchanged = organizationRepository.findById(platform.getId()).orElseThrow();
        assertThat(unchanged.getStatus()).isEqualTo(Status.ACTIVE);
    }

    @Test
    void archivedIsRefused() throws Exception {
        User sa = superAdmin();
        UUID orgId = newOrganization(sa);

        // Only ACTIVE and INACTIVE are offered (ADR-0055): the login gate collapses every non-ACTIVE
        // value, so a third state would add vocabulary without adding behaviour.
        setStatus(sa, orgId, "ARCHIVED", 400);
    }

    @Test
    void aMissingStatusIsRejected() throws Exception {
        User sa = superAdmin();
        UUID orgId = newOrganization(sa);

        mockMvc.perform(patch("/api/v1/platform/organizations/" + orgId + "/status")
                        .header(AUTHORIZATION, bearer(tokenFor(sa)))
                        .contentType(JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void anOrganizationAdminCannotChangeItsOwnOrganizationStatus() throws Exception {
        User sa = superAdmin();
        UUID orgId = newOrganization(sa);
        User admin = createUserInOrganization(orgId, syntheticEmail("status-admin"), PASSWORD,
                Status.ACTIVE, "ADMIN");

        // PLATFORM_ORGANIZATION_UPDATE is a platform-tier authority; an organization administrator
        // must not be able to suspend — or reinstate — its own tenant.
        mockMvc.perform(patch("/api/v1/platform/organizations/" + orgId + "/status")
                        .header(AUTHORIZATION, bearer(tokenFor(admin)))
                        .contentType(JSON)
                        .content("{\"status\":\"INACTIVE\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void unknownOrganizationIsNotFound() throws Exception {
        User sa = superAdmin();
        setStatus(sa, UUID.randomUUID(), "INACTIVE", 404);
    }
}
