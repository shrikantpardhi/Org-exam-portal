package com.company.exam.integration.identity;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.exam.identity.entity.User;
import org.junit.jupiter.api.Test;

/**
 * ADMIN organization-scoped configuration (ADR-0037): read-only, gated on the ADMIN role, and
 * scoped to the caller's organization. It never returns installation-level detail or provider
 * secrets.
 */
class AdminOrgConfigIntegrationTest extends AbstractIdentityIntegrationTest {

    private static final String PASSWORD = "Correct-Horse-Battery-9";

    @Test
    void adminReadsOwnOrganizationConfig() throws Exception {
        User admin = createUser(syntheticEmail("admin"), PASSWORD, "ADMIN");
        mockMvc.perform(get("/api/v1/admin/organization-config").header(AUTHORIZATION, bearer(tokenFor(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.organization.code").value("DEFAULT"))
                .andExpect(jsonPath("$.data.organization.timezone").exists())
                .andExpect(jsonPath("$.data.appVersion").exists())
                .andExpect(jsonPath("$.data.lastMigrationVersion").isNumber())
                .andExpect(jsonPath("$.data.providerStatus.emailConfigured").isBoolean())
                .andExpect(jsonPath("$.data.providerStatus.paymentConfigured").isBoolean())
                .andExpect(jsonPath("$.data.featureFlags").exists());
    }

    @Test
    void nonAdminIsForbidden() throws Exception {
        User student = createUser(syntheticEmail("student"), PASSWORD, "STUDENT");
        mockMvc.perform(get("/api/v1/admin/organization-config").header(AUTHORIZATION, bearer(tokenFor(student))))
                .andExpect(status().isForbidden());
    }
}
