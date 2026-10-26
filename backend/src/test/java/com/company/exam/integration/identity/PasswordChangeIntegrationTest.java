package com.company.exam.integration.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.exam.identity.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * Authenticated password change and the forced first-login gate ({@code specs/05 §72.1}; ADR-0034).
 *
 * <p>A {@code must_change_password} account may reach only the change-password, logout, and me
 * endpoints until it replaces its password; the flag is enforced server-side, never just on the
 * client.
 */
class PasswordChangeIntegrationTest extends AbstractIdentityIntegrationTest {

    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;
    private static final String PASSWORD = "Correct-Horse-Battery-9";
    private static final String NEW_PASSWORD = "Another-Strong-Pass-1";

    @Test
    void changePasswordVerifiesCurrentPasswordAndClearsTheFlag() throws Exception {
        User user = createUser(syntheticEmail("change-ok"), PASSWORD, "STUDENT");
        user.setMustChangePassword(true);
        userRepository.save(user);

        mockMvc.perform(post("/api/v1/auth/change-password")
                        .header(AUTHORIZATION, bearer(tokenFor(user)))
                        .contentType(JSON)
                        .content("{\"currentPassword\":\"" + PASSWORD + "\",\"newPassword\":\"" + NEW_PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Password changed"));

        User reloaded = userRepository.findById(user.getId()).orElseThrow();
        assertThat(reloaded.isMustChangePassword()).isFalse();

        assertThat(login(user.getEmail(), NEW_PASSWORD)).isNotBlank();
        mockMvc.perform(post("/api/v1/auth/login").contentType(JSON)
                        .content("{\"username\":\"" + user.getEmail() + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void changePasswordRejectsAWrongCurrentPassword() throws Exception {
        User user = createUser(syntheticEmail("change-wrong"), PASSWORD, "STUDENT");
        mockMvc.perform(post("/api/v1/auth/change-password")
                        .header(AUTHORIZATION, bearer(tokenFor(user)))
                        .contentType(JSON)
                        .content("{\"currentPassword\":\"Not-The-Right-One-1\",\"newPassword\":\"" + NEW_PASSWORD + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void changePasswordRejectsAWeakNewPassword() throws Exception {
        User user = createUser(syntheticEmail("change-weak"), PASSWORD, "STUDENT");
        mockMvc.perform(post("/api/v1/auth/change-password")
                        .header(AUTHORIZATION, bearer(tokenFor(user)))
                        .contentType(JSON)
                        .content("{\"currentPassword\":\"" + PASSWORD + "\",\"newPassword\":\"short\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("INVALID_PASSWORD"));
    }

    @Test
    void changePasswordExpiresEveryOtherSessionButKeepsTheCallersOwn() throws Exception {
        User user = createUser(syntheticEmail("change-sessions"), PASSWORD, "STUDENT");
        String callerToken = tokenFor(user);
        String otherToken = tokenFor(user);

        mockMvc.perform(get("/api/v1/me").header(AUTHORIZATION, bearer(otherToken)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/change-password")
                        .header(AUTHORIZATION, bearer(callerToken))
                        .contentType(JSON)
                        .content("{\"currentPassword\":\"" + PASSWORD + "\",\"newPassword\":\"" + NEW_PASSWORD + "\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/me").header(AUTHORIZATION, bearer(callerToken)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/me").header(AUTHORIZATION, bearer(otherToken)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void mustChangePasswordFlagBlocksOtherEndpointsUntilThePasswordIsChanged() throws Exception {
        User user = createUser(syntheticEmail("change-gate"), PASSWORD, "ADMIN");
        user.setMustChangePassword(true);
        userRepository.save(user);
        String token = bearer(tokenFor(user));

        // The allow-listed endpoints stay reachable.
        mockMvc.perform(get("/api/v1/me").header(AUTHORIZATION, token)).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/me/permissions").header(AUTHORIZATION, token)).andExpect(status().isOk());

        // Anything else is refused with the forced-change message.
        mockMvc.perform(post("/api/v1/auth/refresh-token").header(AUTHORIZATION, token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Password change required"));

        // Changing the password lifts the gate.
        mockMvc.perform(post("/api/v1/auth/change-password")
                        .header(AUTHORIZATION, token)
                        .contentType(JSON)
                        .content("{\"currentPassword\":\"" + PASSWORD + "\",\"newPassword\":\"" + NEW_PASSWORD + "\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/refresh-token").header(AUTHORIZATION, token))
                .andExpect(status().isOk());
    }
}
