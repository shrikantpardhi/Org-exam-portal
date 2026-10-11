package com.company.exam.integration.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.exam.identity.IdentityConstants;
import com.company.exam.identity.entity.User;
import com.company.exam.identity.service.PasswordResetTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

/**
 * Password reset behaviour ({@code specs/10-P1-Identity-V1.5.md §173, §175; tasks P1-17, P1-18).
 */
class IdentityPasswordResetIntegrationTest extends AbstractIdentityIntegrationTest {

    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;
    private static final String PASSWORD = "Correct-Horse-Battery-9";
    private static final String NEW_PASSWORD = "Another-Strong-Pass-1";

    @Autowired
    private PasswordResetTokenService resetTokenService;

    @Test
    void forgotPasswordNeverRevealsWhetherTheAccountExists() throws Exception {
        User known = createUser(syntheticEmail("known-reset"), PASSWORD, "STUDENT");
        String knownBody = "{\"email\":\"" + known.getEmail() + "\"}";
        String unknownBody = "{\"email\":\"nobody-" + java.util.UUID.randomUUID() + "@example.com\"}";

        mockMvc.perform(post("/api/v1/auth/forgot-password").contentType(JSON).content(knownBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message")
                        .value("If the account exists, password reset instructions have been sent"));
        mockMvc.perform(post("/api/v1/auth/forgot-password").contentType(JSON).content(unknownBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message")
                        .value("If the account exists, password reset instructions have been sent"));
    }

    @Test
    void aValidTokenChangesThePassword() throws Exception {
        User user = createUser(syntheticEmail("reset-valid"), PASSWORD, "STUDENT");
        String token = resetTokenService.issue(user);

        mockMvc.perform(post("/api/v1/auth/reset-password").contentType(JSON)
                        .content(resetBody(token, NEW_PASSWORD)))
                .andExpect(status().isOk());

        assertThat(login(user.getEmail(), NEW_PASSWORD)).isNotBlank();
        mockMvc.perform(post("/api/v1/auth/login").contentType(JSON)
                        .content("{\"username\":\"" + user.getEmail() + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aResetTokenIsSingleUse() throws Exception {
        User user = createUser(syntheticEmail("reset-single"), PASSWORD, "STUDENT");
        String token = resetTokenService.issue(user);

        mockMvc.perform(post("/api/v1/auth/reset-password").contentType(JSON)
                .content(resetBody(token, NEW_PASSWORD))).andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/auth/reset-password").contentType(JSON)
                        .content(resetBody(token, "Yet-Another-Strong-1")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].code").value("INVALID_RESET_TOKEN"));
    }

    @Test
    void anUnknownTokenIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/auth/reset-password").contentType(JSON)
                        .content(resetBody("not-a-real-token", NEW_PASSWORD)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void aWeakPasswordIsRejected() throws Exception {
        User user = createUser(syntheticEmail("reset-weak"), PASSWORD, "STUDENT");
        String token = resetTokenService.issue(user);

        mockMvc.perform(post("/api/v1/auth/reset-password").contentType(JSON)
                        .content(resetBody(token, "short")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("INVALID_PASSWORD"));
    }

    @Test
    void resetInvalidatesExistingSessions() throws Exception {
        User user = createUser(syntheticEmail("reset-sessions"), PASSWORD, "STUDENT");
        String loginToken = login(user.getEmail(), PASSWORD);
        mockMvc.perform(get("/api/v1/me").header(AUTHORIZATION, bearer(loginToken))).andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/reset-password").contentType(JSON)
                        .content(resetBody(resetTokenService.issue(user), NEW_PASSWORD)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/me").header(AUTHORIZATION, bearer(loginToken)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void resetIsRejectedForTheSystemActor() throws Exception {
        User systemActor = userRepository.findById(IdentityConstants.SYSTEM_ACTOR_ID).orElseThrow();
        String token = resetTokenService.issue(systemActor);

        mockMvc.perform(post("/api/v1/auth/reset-password").contentType(JSON)
                        .content(resetBody(token, NEW_PASSWORD)))
                .andExpect(status().isUnprocessableEntity());
    }

    private String resetBody(String token, String newPassword) {
        return "{\"token\":\"" + token + "\",\"newPassword\":\"" + newPassword + "\"}";
    }
}
