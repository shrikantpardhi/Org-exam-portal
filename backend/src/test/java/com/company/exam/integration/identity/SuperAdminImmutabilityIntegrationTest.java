package com.company.exam.integration.identity;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.exam.common.enums.Status;
import com.company.exam.identity.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * SUPER_ADMIN immutability (ADR-0032, 20-P11 §18): the installation owner cannot be archived or
 * have its roles modified by anyone. The cap/immutability check runs before any permission or
 * self-modification policy, so the result is the same for every actor.
 */
class SuperAdminImmutabilityIntegrationTest extends AbstractIdentityIntegrationTest {

    private static final String PASSWORD = "Correct-Horse-Battery-9";

    @Test
    void superAdminCannotBeArchived() throws Exception {
        User superAdmin = createSuperAdmin();
        String adminToken = tokenFor(createUser(syntheticEmail("admin"), PASSWORD, "ADMIN"));

        mockMvc.perform(delete("/api/v1/users/" + superAdmin.getId())
                        .header(AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].code").value("SUPER_ADMIN_IMMUTABLE"));
    }

    @Test
    void superAdminRolesCannotBeModified() throws Exception {
        User superAdmin = createSuperAdmin();
        String adminToken = tokenFor(createUser(syntheticEmail("admin"), PASSWORD, "ADMIN"));

        mockMvc.perform(put("/api/v1/users/" + superAdmin.getId())
                        .header(AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roles\":[]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].code").value("SUPER_ADMIN_IMMUTABLE"));
    }

    private User createSuperAdmin() {
        return createUserInOrganization(organizationRepository.findByCode("PLATFORM").orElseThrow().getId(),
                syntheticEmail("super-admin"), PASSWORD, Status.ACTIVE, "SUPER_ADMIN");
    }
}
