package com.company.exam.integration.identity;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.exam.common.enums.Status;
import com.company.exam.identity.entity.Role;
import com.company.exam.identity.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * D-07: a caller cannot change the status of a role it actively holds
 * ({@code specs/10-P1-Identity-V1.5.md} §63: 403 {@code SELF_PERMISSION_MODIFICATION}).
 *
 * <p>Before the fix {@code PUT /roles/{ownRoleId}} with {@code {"status":"INACTIVE"}} returned 200 and
 * the caller lost every permission granted through that role — including the {@code ROLE_UPDATE} it
 * would need to undo the change. Reproduced live: the reproducing administrator locked itself out and
 * had to be restored by SQL plus a manual cache eviction.
 *
 * <p>Renaming and re-describing stay allowed: they are cosmetic and cannot remove authority. That
 * distinction is asserted here so a future blanket refusal does not silently break ordinary
 * administration.
 */
class SelfRoleStatusIntegrationTest extends AbstractIdentityIntegrationTest {

    private static final String PASSWORD = "Super-Strong-Pass-1";
    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;

    private User anAdmin() {
        return createUser(syntheticEmail("self-status-admin"), PASSWORD, "ADMIN");
    }

    @Test
    void anAdminCannotDeactivateItsOwnRole() throws Exception {
        // The exact call that returned 200 before the fix (D-07).
        User admin = anAdmin();
        String token = bearer(tokenFor(admin));

        mockMvc.perform(put("/api/v1/roles/" + role("ADMIN").getId()).header(AUTHORIZATION, token)
                        .contentType(JSON).content("{\"status\":\"INACTIVE\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errors[0].code").value("SELF_PERMISSION_MODIFICATION"));

        // The role is still ACTIVE, so the caller retains the ability to administer.
        org.assertj.core.api.Assertions.assertThat(role("ADMIN").getStatus()).isEqualTo(Status.ACTIVE);
    }

    @Test
    void anAdminCannotArchiveItsOwnRole() throws Exception {
        String token = bearer(tokenFor(anAdmin()));

        mockMvc.perform(put("/api/v1/roles/" + role("ADMIN").getId()).header(AUTHORIZATION, token)
                        .contentType(JSON).content("{\"status\":\"ARCHIVED\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errors[0].code").value("SELF_PERMISSION_MODIFICATION"));

        org.assertj.core.api.Assertions.assertThat(role("ADMIN").getStatus()).isEqualTo(Status.ACTIVE);
    }

    @Test
    void cosmeticEditsToTheCallersOwnRoleStayAllowed() throws Exception {
        // The rule governs authority, not labels: renaming the caller's own role must keep working,
        // otherwise the fix would be a blanket self-refusal and ordinary administration would break.
        String token = bearer(tokenFor(anAdmin()));
        String before = role("ADMIN").getName();

        mockMvc.perform(put("/api/v1/roles/" + role("ADMIN").getId()).header(AUTHORIZATION, token)
                        .contentType(JSON).content("{\"description\":\"Edited by the self-status test\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.code").value("ADMIN"));

        org.assertj.core.api.Assertions.assertThat(role("ADMIN").getName()).isEqualTo(before);
    }

    @Test
    void anAdminMayStillChangeTheStatusOfARoleItDoesNotHold() throws Exception {
        // The guard is membership-scoped. Deactivating a role the caller is not a member of is
        // ordinary administration and must keep working.
        String token = bearer(tokenFor(anAdmin()));
        Role support = role("SUPPORT");
        Status original = support.getStatus();

        try {
            mockMvc.perform(put("/api/v1/roles/" + support.getId()).header(AUTHORIZATION, token)
                            .contentType(JSON).content("{\"status\":\"INACTIVE\"}"))
                    .andExpect(status().isOk());
        } finally {
            Role restore = role("SUPPORT");
            restore.setStatus(original);
            roleRepository.save(restore);
        }
    }

    @Test
    void theStatusGuardDoesNotReplaceTheImmutabilityGuard() throws Exception {
        // Precedence (ADR-0038): a contract-fixed role is refused with ROLE_IMMUTABLE, not with
        // SELF_PERMISSION_MODIFICATION, because the immutable rule fires first and answers a
        // different question.
        String token = bearer(tokenFor(anAdmin()));

        mockMvc.perform(put("/api/v1/roles/" + role("SUPER_ADMIN").getId()).header(AUTHORIZATION, token)
                        .contentType(JSON).content("{\"status\":\"INACTIVE\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].code").value("ROLE_IMMUTABLE"));
    }
}
