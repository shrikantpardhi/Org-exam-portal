package com.company.exam.integration.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.exam.identity.entity.User;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Login session API ({@code specs/10-P1-Identity-V1.5.md §44, §174; task P1-16).
 */
class IdentitySessionApiIntegrationTest extends AbstractIdentityIntegrationTest {

    private static final String PASSWORD = "Correct-Horse-Battery-9";

    @Test
    void loginCreatesAPersistedSession() throws Exception {
        User user = createUser(syntheticEmail("session-login"), PASSWORD, "STUDENT");

        login(user.getEmail(), PASSWORD);

        assertThat(sessionService.search(user.getId(), null, null, null,
                org.springframework.data.domain.PageRequest.of(0, 10)).getTotalElements()).isEqualTo(1);
    }

    @Test
    void aStudentOnlySeesTheirOwnSessions() throws Exception {
        User student = createUser(syntheticEmail("session-student"), PASSWORD, "STUDENT");
        String other = login(createUser(syntheticEmail("session-other"), PASSWORD, "STUDENT").getEmail(), PASSWORD);
        String studentToken = login(student.getEmail(), PASSWORD);

        assertThat(other).isNotBlank();
        mockMvc.perform(get("/api/v1/login-sessions").header(AUTHORIZATION, bearer(studentToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].userId", everyItem(is(student.getId().toString()))));
    }

    @Test
    void aStudentCannotReadAnotherUsersSession() throws Exception {
        User student = createUser(syntheticEmail("session-nosy"), PASSWORD, "STUDENT");
        User victim = createUser(syntheticEmail("session-victim"), PASSWORD, "STUDENT");
        UUID victimSession = sessionService.create(victim.getId(), "127.0.0.1", "test").getId();
        String token = tokenFor(student);

        mockMvc.perform(get("/api/v1/login-sessions/" + victimSession).header(AUTHORIZATION, bearer(token)))
                .andExpect(status().isForbidden());
    }

    @Test
    void aUserWithSessionViewCanReadAnySession() throws Exception {
        User admin = createUser(syntheticEmail("session-admin"), PASSWORD, "ADMIN");
        User victim = createUser(syntheticEmail("session-victim2"), PASSWORD, "STUDENT");
        UUID victimSession = sessionService.create(victim.getId(), "127.0.0.1", "test").getId();
        String token = tokenFor(admin);

        mockMvc.perform(get("/api/v1/login-sessions/" + victimSession).header(AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(victim.getId().toString()))
                .andExpect(jsonPath("$.data.accessToken").doesNotExist());
    }

    @Test
    void anExpiredSessionIsNoLongerAccepted() throws Exception {
        User user = createUser(syntheticEmail("session-expire"), PASSWORD, "STUDENT");
        UUID sessionId = sessionService.create(user.getId(), "127.0.0.1", "test").getId();
        String token = tokenIssuer.issue(user.getId(), sessionId).token();

        mockMvc.perform(get("/api/v1/me").header(AUTHORIZATION, bearer(token))).andExpect(status().isOk());

        sessionService.expire(sessionId);

        mockMvc.perform(get("/api/v1/me").header(AUTHORIZATION, bearer(token)))
                .andExpect(status().isUnauthorized());
    }
}
