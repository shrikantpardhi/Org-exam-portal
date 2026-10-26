package com.company.exam.integration.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.exam.common.enums.Status;
import com.company.exam.identity.entity.Organization;
import com.company.exam.identity.entity.Role;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * D-05: roles are organization-owned (ADR-0045, Gate B answer B1 = shape S2).
 *
 * <p>S2 semantics under test:
 *
 * <ul>
 *   <li>{@code organization_id IS NULL} — a platform-wide role, visible to and shared by every
 *       organization. The seven seeded roles are these.
 *   <li>{@code organization_id = X} — owned by organization X, visible only to X.
 * </ul>
 *
 * <p><b>The pin on S2's limit is deliberate.</b> {@code aSharedSeededRoleIsStillEditableAcrossOrganizations}
 * asserts the residual S2 was chosen to accept. It is a test of a known defect, not an endorsement: if
 * the project later moves to S1, that test fails, and the failure is the signal to update R2's status.
 */
class OrganizationScopedRoleIntegrationTest extends AbstractIdentityIntegrationTest {

    private static final String PASSWORD = "Correct-Horse-Battery-9";

    private String adminOf(UUID organizationId) {
        return bearer(tokenFor(createUserInOrganization(organizationId,
                syntheticEmail("role-admin"), PASSWORD, Status.ACTIVE, "ADMIN")));
    }

    private Organization newOrganization() {
        return createOrganization("ROLEORG_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8),
                Status.ACTIVE);
    }

    private String createRole(String bearer, String code, String name) throws Exception {
        String body = mockMvc.perform(post("/api/v1/roles")
                        .header(AUTHORIZATION, bearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + code + "\",\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(body, "$.data.id");
    }

    @Test
    void aCreatedRoleBelongsToTheCreatorsOrganization() throws Exception {
        Organization organization = newOrganization();
        String code = "OWN_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        String roleId = createRole(adminOf(organization.getId()), code, "Owned Role");

        Role saved = roleRepository.findById(UUID.fromString(roleId)).orElseThrow();
        assertThat(saved.getOrganizationId()).isEqualTo(organization.getId());
        assertThat(saved.isPlatformWide()).isFalse();
    }

    @Test
    void theRoleCatalogueShowsOwnRolesPlusThePlatformWideOnes() throws Exception {
        Organization organization = newOrganization();
        String code = "VIS_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        String roleId = createRole(adminOf(organization.getId()), code, "Visible Role");

        String body = mockMvc.perform(get("/api/v1/roles?size=200").header(AUTHORIZATION,
                        adminOf(organization.getId())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<String> codes = com.jayway.jsonpath.JsonPath.read(body, "$.data[*].code");

        // Its own role, and the platform-wide seeds it depends on.
        assertThat(codes).contains(code);
        assertThat(codes).contains("STAFF", "STUDENT");
    }

    @Test
    void anotherOrganizationsPrivateRoleIsNotVisible() throws Exception {
        // This is D-05 itself: before the fix, every organization saw every role.
        Organization owner = newOrganization();
        Organization outsider = newOrganization();
        String code = "PRIV_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        createRole(adminOf(owner.getId()), code, "Private Role");

        String body = mockMvc.perform(get("/api/v1/roles?size=200")
                        .header(AUTHORIZATION, adminOf(outsider.getId())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<String> codes = com.jayway.jsonpath.JsonPath.read(body, "$.data[*].code");

        assertThat(codes).doesNotContain(code);
    }

    @Test
    void twoOrganizationsMayEachOwnARoleWithTheSameCode() throws Exception {
        // The reason the global unique on role.code had to become two partial uniques (V35).
        String code = "SAME_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        Organization first = newOrganization();
        Organization second = newOrganization();

        String firstId = createRole(adminOf(first.getId()), code, "Same Code One");
        String secondId = createRole(adminOf(second.getId()), code, "Same Code Two");

        assertThat(firstId).isNotEqualTo(secondId);
        assertThat(roleRepository.findById(UUID.fromString(firstId)).orElseThrow().getOrganizationId())
                .isEqualTo(first.getId());
        assertThat(roleRepository.findById(UUID.fromString(secondId)).orElseThrow().getOrganizationId())
                .isEqualTo(second.getId());
    }

    @Test
    void theSameCodeCannotBeCreatedTwiceWithinOneOrganization() throws Exception {
        Organization organization = newOrganization();
        String code = "DUP_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        createRole(adminOf(organization.getId()), code, "First");

        mockMvc.perform(post("/api/v1/roles").header(AUTHORIZATION, adminOf(organization.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + code + "\",\"name\":\"Second\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void aSharedSeededRoleIsStillEditableAcrossOrganizations() throws Exception {
        // THE ACCEPTED RESIDUAL OF S2 (Gate B memo §8, ADR-0045). The seeded roles stay
        // platform-wide, so an ADMIN of one organization can still rename STAFF and every other
        // organization sees the change. This is why R2 is recorded as PARTIALLY met, not met.
        //
        // The assertion is written as a positive statement of the current behaviour on purpose: a
        // future move to S1 will make this fail, and that failure is the reminder to re-grade R2.
        Role staff = role("STAFF");
        assertThat(staff.isPlatformWide()).isTrue();

        String original = staff.getName();
        try {
            mockMvc.perform(put("/api/v1/roles/" + staff.getId())
                            .header(AUTHORIZATION, adminOf(newOrganization().getId()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"Renamed across organizations\"}"))
                    .andExpect(status().isOk());

            assertThat(roleRepository.findById(staff.getId()).orElseThrow().getName())
                    .isEqualTo("Renamed across organizations");
        } finally {
            Role restore = roleRepository.findById(staff.getId()).orElseThrow();
            restore.setName(original);
            roleRepository.save(restore);
        }
    }

    @Test
    void thePlatformWideSeededRolesRemainResolvableByCode() throws Exception {
        // Every IdentityConstants lookup is now findByCodeAndOrganizationIdIsNull. If V35 had
        // stamped the seeded roles with an organization, those lookups would silently find nothing
        // and authorization would fail closed everywhere.
        for (String code : List.of("ADMIN", "SUPER_ADMIN", "STAFF", "STUDENT", "REVIEWER",
                "SUPPORT", "FINANCE")) {
            assertThat(roleRepository.findByCodeAndOrganizationIdIsNull(code))
                    .as("seeded role %s must be platform-wide", code)
                    .isPresent();
        }
    }
}
