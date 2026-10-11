package com.company.exam.integration.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.exam.common.enums.Status;
import com.company.exam.identity.entity.User;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * User management API ({@code specs/10-P1-Identity-V1.5.md §53-§59, §171; task P1-14).
 */
class IdentityUserApiIntegrationTest extends AbstractIdentityIntegrationTest {

    private static final String PASSWORD = "Correct-Horse-Battery-9";
    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;

    private User anAdmin() {
        return createUser(syntheticEmail("user-admin"), PASSWORD, "ADMIN");
    }

    @Test
    void adminCreatesAUserWithRolesAndNoPasswordIsReturned() throws Exception {
        String token = tokenFor(anAdmin());
        String email = syntheticEmail("created");

        mockMvc.perform(post("/api/v1/users").header(AUTHORIZATION, bearer(token)).contentType(JSON)
                        .content(createBody(email, role("STUDENT").getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.email").value(email))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.roles[0]").value("STUDENT"))
                .andExpect(jsonPath("$.data.password").doesNotExist());

        assertThat(userRepository.existsByEmail(email)).isTrue();
    }

    @Test
    void duplicateEmailIsRejectedWithConflict() throws Exception {
        String token = tokenFor(anAdmin());
        String email = syntheticEmail("dupe");

        mockMvc.perform(post("/api/v1/users").header(AUTHORIZATION, bearer(token)).contentType(JSON)
                .content(createBody(email, role("STUDENT").getId()))).andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/users").header(AUTHORIZATION, bearer(token)).contentType(JSON)
                        .content(createBody(email, role("STUDENT").getId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void invalidEmailIsRejectedWithBadRequest() throws Exception {
        String token = tokenFor(anAdmin());

        mockMvc.perform(post("/api/v1/users").header(AUTHORIZATION, bearer(token)).contentType(JSON)
                        .content(createBody("not-an-email", role("STUDENT").getId())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void userListIsPaginated() throws Exception {
        String token = tokenFor(anAdmin());

        mockMvc.perform(get("/api/v1/users?page=0&size=5").header(AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.pagination.size").value(5))
                .andExpect(jsonPath("$.pagination.page").value(0));
    }

    @Test
    void unsupportedSortFieldIsRejected() throws Exception {
        String token = tokenFor(anAdmin());

        mockMvc.perform(get("/api/v1/users?sort=password,desc").header(AUTHORIZATION, bearer(token)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownUserIsNotFound() throws Exception {
        String token = tokenFor(anAdmin());

        mockMvc.perform(get("/api/v1/users/" + UUID.randomUUID()).header(AUTHORIZATION, bearer(token)))
                .andExpect(status().isNotFound());
    }

    @Test
    void aStudentCannotCreateOrListUsers() throws Exception {
        User student = createUser(syntheticEmail("student-users"), PASSWORD, "STUDENT");
        String token = tokenFor(student);

        mockMvc.perform(get("/api/v1/users").header(AUTHORIZATION, bearer(token))).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/users").header(AUTHORIZATION, bearer(token)).contentType(JSON)
                .content(createBody(syntheticEmail("nope"), role("STUDENT").getId())))
                .andExpect(status().isForbidden());
    }

    @Test
    void profileUpdateChangesOnlyPermittedFields() throws Exception {
        String token = tokenFor(anAdmin());
        User target = createUser(syntheticEmail("target"), PASSWORD, "STUDENT");

        mockMvc.perform(put("/api/v1/users/" + target.getId()).header(AUTHORIZATION, bearer(token))
                        .contentType(JSON).content("{\"firstName\":\"Renamed\",\"lastName\":\"Person\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.firstName").value("Renamed"))
                .andExpect(jsonPath("$.data.email").value(target.getEmail()));
    }

    @Test
    void archiveSetsArchivedStatusAndExpiresSessions() throws Exception {
        String token = tokenFor(anAdmin());
        User target = createUser(syntheticEmail("archive"), PASSWORD, "STUDENT");
        UUID sessionId = sessionService.create(target.getId(), "127.0.0.1", "test").getId();

        mockMvc.perform(delete("/api/v1/users/" + target.getId()).header(AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk());

        assertThat(userRepository.findById(target.getId()).orElseThrow().getStatus()).isEqualTo(Status.ARCHIVED);
        assertThat(sessionService.isActive(sessionId, target.getId())).isFalse();
    }

    @Test
    void aUserCannotModifyTheirOwnRoles() throws Exception {
        User admin = anAdmin();
        String token = tokenFor(admin);

        mockMvc.perform(put("/api/v1/users/" + admin.getId()).header(AUTHORIZATION, bearer(token))
                        .contentType(JSON)
                        .content("{\"roles\":[\"" + role("STUDENT").getId() + "\"]}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errors[0].code").value("SELF_ROLE_MODIFICATION"));
    }

    @Test
    void assigningAdminRequiresTheAdminRoleAssignPermission() throws Exception {
        // An ADMIN has USER_UPDATE but the seed does not grant ADMIN_ROLE_ASSIGN.
        String token = tokenFor(anAdmin());
        User target = createUser(syntheticEmail("escalate"), PASSWORD, "STUDENT");

        mockMvc.perform(put("/api/v1/users/" + target.getId()).header(AUTHORIZATION, bearer(token))
                        .contentType(JSON)
                        .content("{\"roles\":[\"" + role("ADMIN").getId() + "\"]}"))
                .andExpect(status().isForbidden());
    }

    private String createBody(String email, UUID roleId) {
        return "{\"firstName\":\"New\",\"lastName\":\"Person\",\"email\":\"" + email
                + "\",\"mobile\":\"" + randomMobile() + "\",\"roles\":[\"" + roleId + "\"]}";
    }

    private String randomMobile() {
        long value = 6_000_000_000L + (Math.abs(UUID.randomUUID().getMostSignificantBits()) % 3_000_000_000L);
        return Long.toString(value);
    }
}
