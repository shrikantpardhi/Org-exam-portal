package com.company.exam.integration.identity;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.exam.identity.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * D-01: SUPER_ADMIN holds the base catalogue read permissions its own seeds screens require
 * (ADR-0032 amendment, V32).
 *
 * <p>The platform seeds screens ({@code docs/frontend/super-admin-flow.md} §12-§13) render the role
 * catalogue and the permission catalogue read-only. Both endpoints are guarded by
 * {@code specs/10-P1-Identity-V1.5.md} §68 ({@code ROLE_VIEW}, {@code PERMISSION_VIEW}). V29 granted
 * SUPER_ADMIN exactly the eight {@code PLATFORM_*} codes, so both screens returned 403 (D-01).
 *
 * <p>This test pins the grant and, equally important, pins what was NOT granted: SUPER_ADMIN remains
 * unable to author roles until Gate A decides that scope. The exact permission set is pinned
 * separately by {@code SuperAdminPermissionCatalogueTest}.
 */
class SuperAdminCatalogueReadIntegrationTest extends AbstractIdentityIntegrationTest {

    private static final String PASSWORD = "Super-Strong-Pass-1";

    @Test
    void superAdminCanReadTheRoleCatalogue() throws Exception {
        User superAdmin = createUser(syntheticEmail("sa-roles"), PASSWORD, "SUPER_ADMIN");

        mockMvc.perform(get("/api/v1/roles?size=100").header(AUTHORIZATION, bearer(tokenFor(superAdmin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.pagination.totalElements").isNumber());
    }

    @Test
    void superAdminCanReadThePermissionCatalogueIncludingPlatformCodes() throws Exception {
        User superAdmin = createUser(syntheticEmail("sa-perms"), PASSWORD, "SUPER_ADMIN");
        String token = bearer(tokenFor(superAdmin));

        // QuerySupport.MAX_PAGE_SIZE caps size at 100, and the catalogue holds 163 permissions, so
        // the assertion must span pages rather than assume a single page carries the codes.
        java.util.Set<String> codes = new java.util.HashSet<>();
        Integer totalElements = null;
        for (int page = 0; totalElements == null || page * 100 < totalElements; page++) {
            String body = mockMvc.perform(get("/api/v1/permissions?size=100&page=" + page + "&sort=code")
                            .header(AUTHORIZATION, token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").isArray())
                    .andReturn().getResponse().getContentAsString();
            codes.addAll(com.jayway.jsonpath.JsonPath.<java.util.List<String>>read(body, "$.data[*].code"));
            totalElements = com.jayway.jsonpath.JsonPath.read(body, "$.pagination.totalElements");
        }

        org.assertj.core.api.Assertions.assertThat(codes).contains("ROLE_VIEW", "PERMISSION_VIEW", "PLATFORM_SYSTEM_VIEW");
    }

    @Test
    void superAdminRoleAuthoringFollowsTheGateADecision() throws Exception {
        // This test previously pinned the *absence* of ROLE_CREATE / ROLE_UPDATE /
        // ROLE_PERMISSION_ASSIGN, on the argument that the seeds screens are read-only and Gate A had
        // not yet decided whether the installation owner authors roles.
        //
        // Gate A decided (2026-10-26, answer A2): it does, using create-then-assign. V34 grants the
        // three permissions, so the pin inverts. Kept rather than deleted: the pair of assertions —
        // authoring allowed, catalogue read allowed — is what records the decision in executable form.
        User superAdmin = createUser(syntheticEmail("sa-author"), PASSWORD, "SUPER_ADMIN");
        String token = bearer(tokenFor(superAdmin));

        mockMvc.perform(put("/api/v1/roles/" + role("STAFF").getId()).header(AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Renamed by super admin\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/roles?size=5").header(AUTHORIZATION, token))
                .andExpect(status().isOk());
    }
}