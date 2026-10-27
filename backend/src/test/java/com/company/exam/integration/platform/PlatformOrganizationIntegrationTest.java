package com.company.exam.integration.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.exam.identity.entity.User;
import com.company.exam.integration.identity.AbstractIdentityIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * SUPER_ADMIN business-organization endpoints ({@code specs/05 §72.2-§72.5}).
 *
 * <p>PLATFORM is the system organization: it is never listed, never returned by detail, and its
 * code is reserved. The one-ADMIN-per-org cap is enforced through {@code MAX_ADMINS_PER_ORG}.
 */
class PlatformOrganizationIntegrationTest extends AbstractIdentityIntegrationTest {

    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;
    private static final String PASSWORD = "Super-Strong-Pass-1";

    private User superAdmin() {
        return createUser(syntheticEmail("super-admin"), PASSWORD, "SUPER_ADMIN");
    }

    private String uniqueCode(String prefix) {
        return (prefix + "-" + UUID.randomUUID()).toUpperCase().substring(0, 24);
    }

    private String createOrg(User sa, String code, String name) throws Exception {
        return mockMvc.perform(post("/api/v1/platform/organizations")
                        .header(AUTHORIZATION, bearer(tokenFor(sa)))
                        .contentType(JSON)
                        .content("{\"code\":\"" + code + "\",\"name\":\"" + name + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void createOrganizationSucceedsAndNormalizesCode() throws Exception {
        User sa = superAdmin();
        String raw = "acme-" + UUID.randomUUID().toString().substring(0, 8);

        mockMvc.perform(post("/api/v1/platform/organizations")
                        .header(AUTHORIZATION, bearer(tokenFor(sa)))
                        .contentType(JSON)
                        .content("{\"code\":\"" + raw + "\",\"name\":\"Acme Corp\",\"description\":\"A business org\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.code").value(raw.toUpperCase()))
                .andExpect(jsonPath("$.data.name").value("Acme Corp"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.id").isNotEmpty());
    }

    @Test
    void createOrganizationRejectsReservedPlatformCode() throws Exception {
        User sa = superAdmin();
        mockMvc.perform(post("/api/v1/platform/organizations")
                        .header(AUTHORIZATION, bearer(tokenFor(sa)))
                        .contentType(JSON)
                        .content("{\"code\":\"platform\",\"name\":\"Nope\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void createOrganizationRejectsDuplicateCode() throws Exception {
        User sa = superAdmin();
        String code = uniqueCode("DUP");
        String token = bearer(tokenFor(sa));
        String body = "{\"code\":\"" + code + "\",\"name\":\"Dupe Org\"}";

        mockMvc.perform(post("/api/v1/platform/organizations").header(AUTHORIZATION, token)
                        .contentType(JSON).content(body))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/platform/organizations").header(AUTHORIZATION, token)
                        .contentType(JSON).content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void listOrganizationsExcludesPlatformAndIncludesCreatedOrg() throws Exception {
        User sa = superAdmin();
        String code = uniqueCode("LIST");
        createOrg(sa, code, "Listed Org");

        // Scoped by search, not by page: the suite shares one non-rollback database and other tests
        // create organizations, so an unfiltered first page is not guaranteed to contain this one.
        mockMvc.perform(get("/api/v1/platform/organizations?search=" + code)
                        .header(AUTHORIZATION, bearer(tokenFor(sa))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.code == '" + code + "')]", not(empty())))
                .andExpect(jsonPath("$.data[?(@.code == 'PLATFORM')]", empty()))
                .andExpect(jsonPath("$.pagination.totalElements").isNumber());
    }

    @Test
    void organizationDetailReturns404ForPlatformOrganization() throws Exception {
        User sa = superAdmin();
        UUID platformId = organizationRepository.findByCode("PLATFORM").orElseThrow().getId();
        mockMvc.perform(get("/api/v1/platform/organizations/" + platformId)
                        .header(AUTHORIZATION, bearer(tokenFor(sa))))
                .andExpect(status().isNotFound());
    }

    @Test
    void createAdminSucceedsAndStartsWithMustChangePassword() throws Exception {
        User sa = superAdmin();
        String code = uniqueCode("ADM");
        String orgJson = createOrg(sa, code, "Admin Org");
        UUID orgId = UUID.fromString(JsonPath.read(orgJson, "$.data.id"));

        String email = syntheticEmail("admin");
        mockMvc.perform(post("/api/v1/platform/organizations/" + orgId + "/admins")
                        .header(AUTHORIZATION, bearer(tokenFor(sa)))
                        .contentType(JSON)
                        .content("{\"firstName\":\"Ada\",\"lastName\":\"Admin\",\"email\":\"" + email
                                + "\",\"mobile\":\"+919000000001\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(email))
                .andExpect(jsonPath("$.data.userId").isNotEmpty());

        User created = userRepository.findByEmail(email).orElseThrow();
        assertThat(created.isMustChangePassword()).isTrue();
        assertThat(created.getOrganizationId()).isEqualTo(orgId);
        assertThat(userRoleRepository.countActiveAssignmentsByRoleCodeInOrganization("ADMIN", orgId)).isEqualTo(1);
    }

    @Test
    void createAdminEnforcesTheAdministratorCap() throws Exception {
        User sa = superAdmin();
        String token = bearer(tokenFor(sa));
        String code = uniqueCode("CAP");
        String orgJson = createOrg(sa, code, "Cap Org");
        UUID orgId = UUID.fromString(JsonPath.read(orgJson, "$.data.id"));

        // ADR-0054 raised MAX_ADMINS_PER_ORG from 1 to 5 (V42) so an organization can hold several
        // administrators with one designated primary. The cap still exists and is still enforced; this
        // pins the new number rather than deleting the assertion, because an unbounded admin count is
        // exactly the failure this guard prevents.
        // Five succeed; the sixth is refused. Asserting the behaviour rather than reading the rule keeps
        // the test honest if the seed changes again, while still failing loudly if the cap stops working.
        int cap = 5;
        for (int index = 0; index < cap; index++) {
            mockMvc.perform(post("/api/v1/platform/organizations/" + orgId + "/admins")
                            .header(AUTHORIZATION, token).contentType(JSON)
                            .content("{\"firstName\":\"Cap\",\"lastName\":\"Admin\",\"email\":\""
                                    + syntheticEmail("cap-" + index) + "\"}"))
                    .andExpect(status().isOk());
        }

        // The next one is refused: the organization is at its cap.
        mockMvc.perform(post("/api/v1/platform/organizations/" + orgId + "/admins")
                        .header(AUTHORIZATION, token).contentType(JSON)
                        .content("{\"firstName\":\"Over\",\"lastName\":\"Cap\",\"email\":\""
                                + syntheticEmail("cap-over") + "\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void createAdminRejectsDuplicateEmail() throws Exception {
        User sa = superAdmin();
        String email = syntheticEmail("dup-admin");
        String code = uniqueCode("DUPMAIL");
        UUID orgId = UUID.fromString(JsonPath.read(createOrg(sa, code, "Dup Org"), "$.data.id"));

        mockMvc.perform(post("/api/v1/platform/organizations/" + orgId + "/admins")
                        .header(AUTHORIZATION, bearer(tokenFor(sa))).contentType(JSON)
                        .content("{\"firstName\":\"One\",\"lastName\":\"Admin\",\"email\":\"" + email + "\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/platform/organizations/" + orgId + "/admins")
                        .header(AUTHORIZATION, bearer(tokenFor(sa))).contentType(JSON)
                        .content("{\"firstName\":\"Two\",\"lastName\":\"Admin\",\"email\":\"" + email + "\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void platformEndpointsRequireSuperAdminPermission() throws Exception {
        User student = createUser(syntheticEmail("student"), PASSWORD, "STUDENT");
        mockMvc.perform(post("/api/v1/platform/organizations")
                        .header(AUTHORIZATION, bearer(tokenFor(student)))
                        .contentType(JSON)
                        .content("{\"code\":\"" + uniqueCode("DENY") + "\",\"name\":\"Denied\"}"))
                .andExpect(status().isForbidden());
    }
}
