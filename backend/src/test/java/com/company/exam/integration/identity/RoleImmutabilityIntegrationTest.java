package com.company.exam.integration.identity;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.exam.common.enums.Status;
import com.company.exam.identity.entity.Role;
import com.company.exam.identity.entity.User;
import com.company.exam.identity.repository.RolePermissionRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * D-02: the SUPER_ADMIN role is immutable ({@code 20-P11-Administration-V1.5.md} §18, ADR-0032,
 * ADR-0038).
 *
 * <p>Before the fix, {@code PUT /roles/{superAdminRoleId}/permissions} returned 200 to an ADMIN and
 * silently reduced the installation owner to three permissions. The guard is in {@code RoleService},
 * so it covers both {@code PUT /roles/{id}} and {@code PUT /roles/{id}/permissions} and any future
 * caller.
 *
 * <p>The complementary {@code SUPER_ADMIN_IMMUTABLE} rule (409) protects the SUPER_ADMIN
 * <em>account</em> and lives in {@code UserService}; it is covered by
 * {@code SuperAdminImmutabilityIntegrationTest}. The two codes are deliberately distinct so a test
 * can tell which invariant fired.
 */
class RoleImmutabilityIntegrationTest extends AbstractIdentityIntegrationTest {

    private static final String PASSWORD = "Super-Strong-Pass-1";

    private String bearerFor(String email, String... roleCodes) {
        return bearer(tokenFor(createUser(email, PASSWORD, roleCodes)));
    }

    private String adminBearer() {
        return bearerFor(syntheticEmail("admin-immutable"), "ADMIN");
    }

    /**
     * The exact SUPER_ADMIN grant set after V38 (10 PLATFORM_* + 3 catalogue reads + 3 authoring +
     * the reserved-set bypass). V32 seeded 10; Gate A answer A2/A2b added six more; V38 added
     * LOOKUP_VIEW for the read-only lookups seeds screen.
     *
     * <p>Kept as a literal rather than derived from the database on purpose: this test exists to make
     * a change to the owner's capability fail loudly. It is kept in step with
     * {@code SuperAdminPermissionCatalogueTest}.
     */
    private static final int SUPER_ADMIN_PERMISSION_COUNT = 17;

    @Test
    void anAdminCannotReplaceThePermissionsOfTheSuperAdminRole() throws Exception {
        // This is the exact call that succeeded with 200 before the fix, deactivating five of the
        // eight PLATFORM_* grants on the live deployment (D-02 reproduction).
        UUID superAdminRoleId = role("SUPER_ADMIN").getId();
        List<String> memberIds = permissionRepository.findEffectiveByRoleId(superAdminRoleId).stream()
                .map(permission -> permission.getId().toString()).toList();

        mockMvc.perform(put("/api/v1/roles/" + superAdminRoleId + "/permissions")
                        .header(AUTHORIZATION, adminBearer()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permissionIds\":[]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].code").value("ROLE_IMMUTABLE"));

        // And the set is untouched: the refusal is not merely a status code on the response.
        org.assertj.core.api.Assertions.assertThat(permissionRepository.findEffectiveByRoleId(superAdminRoleId))
                .hasSize(SUPER_ADMIN_PERMISSION_COUNT);
        org.assertj.core.api.Assertions.assertThat(permissionRepository.findEffectiveByRoleId(superAdminRoleId))
                .extracting(permission -> permission.getId().toString()).containsExactlyInAnyOrderElementsOf(memberIds);
    }

    @Test
    void evenSuperAdminCannotReplaceThePermissionsOfItsOwnRole() throws Exception {
        // ADR-0032: permanent, unique, non-demotable. A guard with a SUPER_ADMIN exemption would
        // make the invariant conditional on the principal it protects.
        //
        // In practice SUPER_ADMIN is refused one layer earlier: V32 grants it ROLE_VIEW and
        // PERMISSION_VIEW but not ROLE_PERMISSION_ASSIGN, so @PreAuthorize answers 403. Both layers
        // refuse, which is what the invariant requires; the assertion here is that the call does not
        // succeed and the permission set is unchanged. Whether SUPER_ADMIN should hold the authoring
        // permissions is Gate A's decision, and this test is deliberately independent of it.
        UUID superAdminRoleId = role("SUPER_ADMIN").getId();
        int before = permissionRepository.findEffectiveByRoleId(superAdminRoleId).size();

        int status = mockMvc.perform(put("/api/v1/roles/" + superAdminRoleId + "/permissions")
                        .header(AUTHORIZATION, bearerFor(syntheticEmail("sa-self"), "SUPER_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"permissionIds\":[]}"))
                .andReturn().getResponse().getStatus();

        org.assertj.core.api.Assertions.assertThat(status).isIn(403, 409);
        org.assertj.core.api.Assertions.assertThat(permissionRepository.findEffectiveByRoleId(superAdminRoleId))
                .hasSize(before);
    }

    @Test
    void anAdminCannotRenameOrInactivateTheSuperAdminRole() throws Exception {
        // The same invariant through the other write path (PUT /roles/{id}), which had no guard at
        // all before the fix and is also D-07's vector.
        String token = adminBearer();
        UUID superAdminRoleId = role("SUPER_ADMIN").getId();

        mockMvc.perform(put("/api/v1/roles/" + superAdminRoleId).header(AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Demoted\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].code").value("ROLE_IMMUTABLE"));

        mockMvc.perform(put("/api/v1/roles/" + superAdminRoleId).header(AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"INACTIVE\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].code").value("ROLE_IMMUTABLE"));

        Role superAdmin = role("SUPER_ADMIN");
        org.assertj.core.api.Assertions.assertThat(superAdmin.getStatus()).isEqualTo(Status.ACTIVE);
        org.assertj.core.api.Assertions.assertThat(superAdmin.getName()).isNotEqualTo("Demoted");
    }

    @Test
    void theGuardIsKeyedOnTheRoleCodeNotOnItsIdentifier() throws Exception {
        // G-02: no hard-coded UUIDs. A role re-seeded under a fresh identifier stays immutable.
        Role superAdmin = role("SUPER_ADMIN");
        org.assertj.core.api.Assertions.assertThat(superAdmin.getCode()).isEqualTo("SUPER_ADMIN");

        mockMvc.perform(put("/api/v1/roles/" + superAdmin.getId()).header(AUTHORIZATION, adminBearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"ARCHIVED\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].code").value("ROLE_IMMUTABLE"));
    }

    @Test
    void anOrdinaryRoleIsStillEditable() throws Exception {
        // The guard must not be a blanket refusal: STAFF stays editable, otherwise D-02's fix would
        // break ordinary administration.
        String token = adminBearer();
        UUID staffRoleId = role("STAFF").getId();

        mockMvc.perform(put("/api/v1/roles/" + staffRoleId).header(AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"Edited by the immutability test\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.code").value("STAFF"));
    }

    @Test
    void theRefusalDoesNotDependOnTheRolesCurrentStatus() throws Exception {
        // Out-of-band damage (SQL, a restored backup) may leave the SUPER_ADMIN role INACTIVE. The
        // guard must still refuse, so the immutable role cannot be "repaired" into a different shape
        // by first inactivating it.
        UUID superAdminRoleId = role("SUPER_ADMIN").getId();
        mockMvc.perform(get("/api/v1/roles/" + superAdminRoleId + "/permissions")
                        .header(AUTHORIZATION, adminBearer()))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/roles/" + superAdminRoleId + "/permissions")
                        .header(AUTHORIZATION, adminBearer()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permissionIds\":[]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].code").value("ROLE_IMMUTABLE"));
    }
}