package com.company.exam.integration.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.exam.common.enums.Status;
import com.company.exam.identity.entity.Organization;
import com.company.exam.identity.entity.User;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * Authentication endpoint behaviour ({@code specs/10-P1-Identity-V1.5.md §169, §172, §175};
 * tasks P1-10, P1-11, P1-12, P1-13).
 */
class IdentityAuthenticationIntegrationTest extends AbstractIdentityIntegrationTest {

    private static final String PASSWORD = "Correct-Horse-Battery-9";

    @Test
    void loginSucceedsForAnActiveUserWithCorrectCredentials() throws Exception {
        User user = createUser(syntheticEmail("login"), PASSWORD, "STUDENT");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(user.getEmail(), PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Login successful"))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.user.roles[0]").value("STUDENT"))
                .andExpect(jsonPath("$.data.user.password").doesNotExist());
    }

    @Test
    void loginRejectsWrongPasswordWithoutRevealingWhy() throws Exception {
        User user = createUser(syntheticEmail("wrongpass"), PASSWORD, "STUDENT");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(user.getEmail(), "Not-The-Password-123")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Invalid credentials"));
    }

    @Test
    void loginRejectsUnknownAccountWithTheSameGenericError() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("nobody-" + java.util.UUID.randomUUID() + "@example.com", PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid credentials"));
    }

    @Test
    void loginRejectsInactiveAndArchivedUsers() throws Exception {
        User inactive = createUserInOrganization(defaultOrganizationId(), syntheticEmail("inactive"), PASSWORD,
                Status.INACTIVE, "STUDENT");
        User archived = createUserInOrganization(defaultOrganizationId(), syntheticEmail("archived"), PASSWORD,
                Status.ARCHIVED, "STUDENT");

        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(loginBody(inactive.getEmail(), PASSWORD))).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(loginBody(archived.getEmail(), PASSWORD))).andExpect(status().isUnauthorized());
    }

    @Test
    void loginIsRejectedForTheSystemActor() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("system@platform.internal", PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid credentials"));
    }

    @Test
    void loginIsRejectedWhenTheOrganizationIsInactive() throws Exception {
        Organization inactiveOrg = createOrganization("INACTIVE-" + java.util.UUID.randomUUID(), Status.INACTIVE);
        User user = createUserInOrganization(inactiveOrg.getId(), syntheticEmail("orginactive"), PASSWORD,
                Status.ACTIVE, "STUDENT");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(user.getEmail(), PASSWORD)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void currentUserReturnsSafeProfileAndRequiresAuthentication() throws Exception {
        User user = createUser(syntheticEmail("me"), PASSWORD, "STUDENT");
        String token = login(user.getEmail(), PASSWORD);

        mockMvc.perform(get("/api/v1/me").header(AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(user.getId().toString()))
                .andExpect(jsonPath("$.data.email").value(user.getEmail()))
                .andExpect(jsonPath("$.data.roles[0]").value("STUDENT"))
                .andExpect(jsonPath("$.data.password").doesNotExist());

        mockMvc.perform(get("/api/v1/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void currentPermissionsReflectEffectivePermissions() throws Exception {
        User admin = createUser(syntheticEmail("admin-me"), PASSWORD, "ADMIN");
        String adminToken = login(admin.getEmail(), PASSWORD);

        mockMvc.perform(get("/api/v1/me/permissions").header(AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.permissions").isArray())
                .andExpect(jsonPath("$.data.permissions",
                        org.hamcrest.Matchers.hasItem("USER_VIEW")));
    }

    @Test
    void aStudentHasNoAdministrativePermissions() throws Exception {
        User student = createUser(syntheticEmail("student-perm"), PASSWORD, "STUDENT");
        String token = login(student.getEmail(), PASSWORD);

        // The intent of this test is in its name: a student holds nothing administrative. It used to
        // assert an empty set, which happened to be true and was stricter than the contract. ADR-0030
        // grants exactly one self-service report permission (V28) because P1 §9 says not to encode
        // business authorization as a role branch, and P1 §66 says students receive "self-service
        // session and current-user permissions only" — which a self-service report read satisfies.
        mockMvc.perform(get("/api/v1/me/permissions").header(AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.permissions").value(
                        org.hamcrest.Matchers.containsInAnyOrder("REPORT_STUDENT_READ")))
                // No administrative capability of any kind.
                .andExpect(jsonPath("$.data.permissions").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.hasItem(org.hamcrest.Matchers.startsWith("USER_")))))
                .andExpect(jsonPath("$.data.permissions").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.hasItem(org.hamcrest.Matchers.startsWith("ROLE_")))))
                .andExpect(jsonPath("$.data.permissions").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.hasItem(org.hamcrest.Matchers.startsWith("AUDIT_")))))
                .andExpect(jsonPath("$.data.permissions").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.hasItem("ADMIN_ROLE_ASSIGN"))));
    }

    @Test
    void logoutEndsTheSessionAndInvalidatesTheToken() throws Exception {
        User user = createUser(syntheticEmail("logout"), PASSWORD, "STUDENT");
        String token = login(user.getEmail(), PASSWORD);

        mockMvc.perform(get("/api/v1/me").header(AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/auth/logout").header(AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/me").header(AUTHORIZATION, bearer(token)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aSecondLogoutWithAnEndedSessionIsSafe() throws Exception {
        User user = createUser(syntheticEmail("logout2"), PASSWORD, "STUDENT");
        String token = login(user.getEmail(), PASSWORD);

        mockMvc.perform(post("/api/v1/auth/logout").header(AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk());
        // The session is already terminal, so the token is anonymous; the business state is unchanged.
        mockMvc.perform(post("/api/v1/auth/logout").header(AUTHORIZATION, bearer(token)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshReissuesAnAccessTokenForAnActiveSession() throws Exception {
        User user = createUser(syntheticEmail("refresh"), PASSWORD, "STUDENT");
        String token = login(user.getEmail(), PASSWORD);

        String body = mockMvc.perform(post("/api/v1/auth/refresh-token").header(AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        String refreshed = JsonPath.read(body, "$.data.accessToken");
        assertThat(refreshed).isNotBlank();
        mockMvc.perform(get("/api/v1/me").header(AUTHORIZATION, bearer(refreshed)))
                .andExpect(status().isOk());
    }

    private String loginBody(String username, String password) {
        return "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
    }
}
