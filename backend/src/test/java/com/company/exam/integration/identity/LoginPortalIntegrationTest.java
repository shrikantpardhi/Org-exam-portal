package com.company.exam.integration.identity;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.exam.identity.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * Login {@code portal} discriminator (ADR-0035): a signed-in account may only enter the portal
 * matching its role. An absent portal keeps the pre-existing behavior; an unknown portal is a
 * validation error; a wrong portal is a generic 401 that never names the correct portal.
 */
class LoginPortalIntegrationTest extends AbstractIdentityIntegrationTest {

    private static final String PASSWORD = "Correct-Horse-Battery-9";

    @Test
    void studentLoginOnStudentPortalSucceeds() throws Exception {
        User student = createUser(syntheticEmail("student"), PASSWORD, "STUDENT");
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(student.getEmail(), PASSWORD, "STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").exists());
    }

    @Test
    void adminLoginOnOrganizationAdminPortalSucceeds() throws Exception {
        User admin = createUser(syntheticEmail("admin"), PASSWORD, "ADMIN");
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(admin.getEmail(), PASSWORD, "ORGANIZATION_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").exists());
    }

    @Test
    void loginWithoutPortalKeepsWorking() throws Exception {
        User student = createUser(syntheticEmail("student"), PASSWORD, "STUDENT");
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + student.getEmail() + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").exists());
    }

    @Test
    void studentLoginOnSystemAdminPortalIsRejected() throws Exception {
        User student = createUser(syntheticEmail("student"), PASSWORD, "STUDENT");
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(student.getEmail(), PASSWORD, "SYSTEM_ADMIN")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errors[0].code").value("PORTAL_MISMATCH"));
    }

    @Test
    void unknownPortalIsRejected() throws Exception {
        User student = createUser(syntheticEmail("student"), PASSWORD, "STUDENT");
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(student.getEmail(), PASSWORD, "BOGUS")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("INVALID_PORTAL"));
    }

    private String loginBody(String username, String password, String portal) {
        return "{\"username\":\"" + username + "\",\"password\":\"" + password + "\",\"portal\":\"" + portal + "\"}";
    }
}
