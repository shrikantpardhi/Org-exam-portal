package com.company.exam.integration.platform;

import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.exam.identity.entity.User;
import com.company.exam.integration.identity.AbstractIdentityIntegrationTest;
import org.junit.jupiter.api.Test;

/**
 * SUPER_ADMIN platform status and system information ({@code specs/05 §72.6, §72.7}).
 */
class PlatformInfoIntegrationTest extends AbstractIdentityIntegrationTest {

    private static final String PASSWORD = "Super-Strong-Pass-1";

    @Test
    void statusReportsCountsSeedAndHealth() throws Exception {
        User sa = createUser(syntheticEmail("sa-status"), PASSWORD, "SUPER_ADMIN");
        mockMvc.perform(get("/api/v1/platform/status")
                        .header(AUTHORIZATION, bearer(tokenFor(sa))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.organizationCount").isNumber())
                .andExpect(jsonPath("$.data.adminCount").isNumber())
                .andExpect(jsonPath("$.data.userCount").isNumber())
                .andExpect(jsonPath("$.data.seedStatus").value("COMPLETE"))
                .andExpect(jsonPath("$.data.systemHealth").isNotEmpty());
    }

    @Test
    void systemReportsVersionMigrationUptimeAndDependencies() throws Exception {
        User sa = createUser(syntheticEmail("sa-system"), PASSWORD, "SUPER_ADMIN");
        mockMvc.perform(get("/api/v1/platform/system")
                        .header(AUTHORIZATION, bearer(tokenFor(sa))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.appVersion").isNotEmpty())
                .andExpect(jsonPath("$.data.frontendVersion").isNotEmpty())
                .andExpect(jsonPath("$.data.migrationVersion").isNumber())
                .andExpect(jsonPath("$.data.lastMigration").isNotEmpty())
                .andExpect(jsonPath("$.data.uptimeSeconds").isNumber())
                .andExpect(jsonPath("$.data.dependencies[?(@.name == 'postgresql')]", not(empty())))
                .andExpect(jsonPath("$.data.dependencies[?(@.name == 'redis')]", not(empty())));
    }

    @Test
    void statusRequiresSuperAdminPermission() throws Exception {
        User student = createUser(syntheticEmail("student-status"), PASSWORD, "STUDENT");
        mockMvc.perform(get("/api/v1/platform/status")
                        .header(AUTHORIZATION, bearer(tokenFor(student))))
                .andExpect(status().isForbidden());
    }
}
