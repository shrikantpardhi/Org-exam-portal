package com.company.exam.integration.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.exam.common.enums.Status;
import com.company.exam.identity.entity.Organization;
import com.company.exam.identity.entity.User;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * D-10: the user list and detail are scoped to the caller's organization (R2).
 *
 * <p>Before the fix, a DEFAULT-organization ADMIN received all 5,010 users of a two-organization
 * deployment through {@code GET /users}, and could read the installation owner by id. Those numbers
 * are from the live database; this test uses a synthetic second organization so the assertion does
 * not depend on seed contents.
 */
class UserListOrganizationScopeIntegrationTest extends AbstractIdentityIntegrationTest {

    private static final String PASSWORD = "Correct-Horse-Battery-9";

    private String adminOf(UUID organizationId) {
        return bearer(tokenFor(createUserInOrganization(organizationId,
                syntheticEmail("scope-admin"), PASSWORD, Status.ACTIVE, "ADMIN")));
    }

    private Organization otherOrganization() {
        return createOrganization("SCOPE_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8),
                Status.ACTIVE);
    }

    @Test
    void theUserListReturnsOnlyTheCallersOrganization() throws Exception {
        Organization other = otherOrganization();
        User foreign = createUserInOrganization(other.getId(), syntheticEmail("foreign-user"), PASSWORD,
                Status.ACTIVE, "STUDENT");
        UUID myOrgId = defaultOrganizationId();

        String body = mockMvc.perform(get("/api/v1/users?size=100").header(AUTHORIZATION, adminOf(myOrgId)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        List<String> emails = com.jayway.jsonpath.JsonPath.read(body, "$.data[*].email");

        // The foreign user is the one that used to leak; assert on it directly rather than on a
        // count, because the shared database makes any count order-dependent.
        assertThat(emails).doesNotContain(foreign.getEmail());
        assertThat(emails).isNotEmpty();
    }

    @Test
    void aUserInAnotherOrganizationReturns404RatherThan403() throws Exception {
        // 404, not 403: a 403 would confirm the id exists. Same non-disclosure rule as §25.
        Organization other = otherOrganization();
        User foreign = createUserInOrganization(other.getId(), syntheticEmail("foreign-detail"), PASSWORD,
                Status.ACTIVE, "STUDENT");
        String admin = adminOf(defaultOrganizationId());

        mockMvc.perform(get("/api/v1/users/" + foreign.getId()).header(AUTHORIZATION, admin))
                .andExpect(status().isNotFound());
    }

    @Test
    void aUserInTheCallersOwnOrganizationIsStillReadable() throws Exception {
        // The guard must not be a blanket 404.
        UUID myOrgId = defaultOrganizationId();
        User colleague = createUserInOrganization(myOrgId, syntheticEmail("colleague"), PASSWORD,
                Status.ACTIVE, "STUDENT");

        mockMvc.perform(get("/api/v1/users/" + colleague.getId()).header(AUTHORIZATION, adminOf(myOrgId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(colleague.getEmail()));
    }

    @Test
    void theListCannotBeWidenedByARequestParameter() throws Exception {
        // The scope comes from the actor, never from the query string. Before the fix an ADMIN
        // could already read everything; this pins that adding a parameter does not reintroduce it.
        Organization other = otherOrganization();
        User foreign = createUserInOrganization(other.getId(), syntheticEmail("foreign-param"), PASSWORD,
                Status.ACTIVE, "STUDENT");

        String body = mockMvc.perform(get("/api/v1/users?size=100&organizationId=" + other.getId())
                        .header(AUTHORIZATION, adminOf(defaultOrganizationId())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        List<String> emails = com.jayway.jsonpath.JsonPath.read(body, "$.data[*].email");
        assertThat(emails).doesNotContain(foreign.getEmail());
    }

    @Test
    void theListIsNotScopedBySeedAssumptions() throws Exception {
        // Regression guard for the shape of the fix: the specification must carry an organization
        // predicate, so a caller whose organization has no users gets an empty page rather than
        // everything. This is the fail-closed direction (G-10).
        Organization empty = otherOrganization();
        String body = mockMvc.perform(get("/api/v1/users?size=10")
                        .header(AUTHORIZATION, adminOf(empty.getId())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Integer total = com.jayway.jsonpath.JsonPath.read(body, "$.pagination.totalElements");
        // The organization has no users other than possibly the admin created for this test, which
        // is created in it, so exactly one. Never 5,010.
        assertThat(total).isLessThan(5);
    }
}
