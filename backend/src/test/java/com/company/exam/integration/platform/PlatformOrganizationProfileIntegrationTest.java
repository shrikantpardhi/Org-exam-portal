package com.company.exam.integration.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.exam.common.enums.Status;
import com.company.exam.identity.entity.Organization;
import com.company.exam.identity.entity.User;
import com.company.exam.integration.identity.AbstractIdentityIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * Organization profile at creation and correction afterwards (ADR-0056, §72.2 and §72.17).
 *
 * <p>The profile columns have existed since {@code V37} (ADR-0051) and were visible on the detail
 * screen, but only the <em>tenant</em> ADMIN could write them. A platform owner registering an
 * institute had to create a bare record and then leave the address to someone else — or never have
 * it recorded at all. These tests pin both halves: the profile is accepted at creation, and the
 * platform owner can correct it afterwards.
 */
class PlatformOrganizationProfileIntegrationTest extends AbstractIdentityIntegrationTest {

    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;
    private static final String PASSWORD = "Super-Strong-Pass-1";

    private User superAdmin() {
        return createUser(syntheticEmail("profile-super"), PASSWORD, "SUPER_ADMIN");
    }

    private static String unicCode() {
        return "PRF-" + UUID.randomUUID().toString().toUpperCase().substring(0, 16);
    }

    private static final String FULL_PROFILE = "\",\"name\":\"Acme Institute\",\"description\":\"A place\","
            + "\"addressLine1\":\"12 Main Street\",\"addressLine2\":\"Suite 4\",\"city\":\"Pune\","
            + "\"stateRegion\":\"Maharashtra\",\"postalCode\":\"411001\",\"country\":\"India\","
            + "\"contactEmail\":\"office@acme.test\",\"contactPhone\":\"+91-20-1234\","
            + "\"websiteUrl\":\"https://acme.test\",\"logoUrl\":\"https://acme.test/logo.png\"";

    @Test
    void anOrganizationIsCreatedWithItsWholeProfile() throws Exception {
        User sa = superAdmin();
        String code = unicCode();

        String body = mockMvc.perform(post("/api/v1/platform/organizations")
                        .header(AUTHORIZATION, bearer(tokenFor(sa)))
                        .contentType(JSON)
                        .content("{\"code\":\"" + code + FULL_PROFILE + "}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(JsonPath.read(body, "$.data.id"));

        // Every field must survive the round trip: a partial save would be worse than none, because
        // the operator would believe the address was recorded.
        mockMvc.perform(get("/api/v1/platform/organizations/" + id)
                        .header(AUTHORIZATION, bearer(tokenFor(sa))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Acme Institute"))
                .andExpect(jsonPath("$.data.description").value("A place"))
                .andExpect(jsonPath("$.data.addressLine1").value("12 Main Street"))
                .andExpect(jsonPath("$.data.addressLine2").value("Suite 4"))
                .andExpect(jsonPath("$.data.city").value("Pune"))
                .andExpect(jsonPath("$.data.stateRegion").value("Maharashtra"))
                .andExpect(jsonPath("$.data.postalCode").value("411001"))
                .andExpect(jsonPath("$.data.country").value("India"))
                .andExpect(jsonPath("$.data.contactEmail").value("office@acme.test"))
                .andExpect(jsonPath("$.data.contactPhone").value("+91-20-1234"))
                .andExpect(jsonPath("$.data.websiteUrl").value("https://acme.test"))
                .andExpect(jsonPath("$.data.logoUrl").value("https://acme.test/logo.png"));
    }

    @Test
    void anOrganizationCanBeCreatedWithOnlyCodeAndName() throws Exception {
        User sa = superAdmin();
        String code = unicCode();

        // Every profile column is NULLable (V37): requiring them would contradict the schema decision
        // and reject an organization that legitimately has no public address.
        String body = mockMvc.perform(post("/api/v1/platform/organizations")
                        .header(AUTHORIZATION, bearer(tokenFor(sa)))
                        .contentType(JSON)
                        .content("{\"code\":\"" + code + "\",\"name\":\"Bare Institute\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(JsonPath.read(body, "$.data.id"));

        Organization saved = organizationRepository.findById(id).orElseThrow();
        assertThat(saved.getAddressLine1()).isNull();
        assertThat(saved.getCity()).isNull();
        assertThat(saved.getContactEmail()).isNull();
    }

    @Test
    void anEmptyStringIsStoredAsNull() throws Exception {
        User sa = superAdmin();
        String code = unicCode();

        String body = mockMvc.perform(post("/api/v1/platform/organizations")
                        .header(AUTHORIZATION, bearer(tokenFor(sa)))
                        .contentType(JSON)
                        .content("{\"code\":\"" + code + "\",\"name\":\"Blank Institute\",\"city\":\"  \"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(JsonPath.read(body, "$.data.id"));

        // "" and null must not be two different stored states: one would render as "Not set" and the
        // other as an empty row, for the same operator decision.
        assertThat(organizationRepository.findById(id).orElseThrow().getCity()).isNull();
    }

    @Test
    void anInvalidContactEmailIsRejected() throws Exception {
        User sa = superAdmin();
        mockMvc.perform(post("/api/v1/platform/organizations")
                        .header(AUTHORIZATION, bearer(tokenFor(sa)))
                        .contentType(JSON)
                        .content("{\"code\":\"" + unicCode() + "\",\"name\":\"Bad Mail\",\"contactEmail\":\"not-an-email\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void anOverlongProfileFieldIsRejected() throws Exception {
        User sa = superAdmin();
        String longCity = "x".repeat(101);
        mockMvc.perform(post("/api/v1/platform/organizations")
                        .header(AUTHORIZATION, bearer(tokenFor(sa)))
                        .contentType(JSON)
                        .content("{\"code\":\"" + unicCode() + "\",\"name\":\"Too Long\",\"city\":\"" + longCity + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void thePlatformOwnerCanCorrectAProfile() throws Exception {
        User sa = superAdmin();
        UUID id = createOrganization(sa, unicCode(), "Original Name");

        mockMvc.perform(patch("/api/v1/platform/organizations/" + id)
                        .header(AUTHORIZATION, bearer(tokenFor(sa)))
                        .contentType(JSON)
                        .content("{\"name\":\"Corrected Name\",\"city\":\"Mumbai\",\"contactEmail\":\"new@acme.test\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Organization updated"))
                .andExpect(jsonPath("$.data.name").value("Corrected Name"))
                .andExpect(jsonPath("$.data.city").value("Mumbai"))
                .andExpect(jsonPath("$.data.contactEmail").value("new@acme.test"));
    }

    @Test
    void anOmittedFieldIsLeftAlone() throws Exception {
        User sa = superAdmin();
        String code = unicCode();
        String body = mockMvc.perform(post("/api/v1/platform/organizations")
                        .header(AUTHORIZATION, bearer(tokenFor(sa)))
                        .contentType(JSON)
                        .content("{\"code\":\"" + code + "\",\"name\":\"Keep Me\",\"city\":\"Delhi\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(JsonPath.read(body, "$.data.id"));

        // A PATCH that mentions only the phone must not clear the city it never mentioned.
        mockMvc.perform(patch("/api/v1/platform/organizations/" + id)
                        .header(AUTHORIZATION, bearer(tokenFor(sa)))
                        .contentType(JSON)
                        .content("{\"contactPhone\":\"12345\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.city").value("Delhi"))
                .andExpect(jsonPath("$.data.name").value("Keep Me"))
                .andExpect(jsonPath("$.data.contactPhone").value("12345"));
    }

    @Test
    void aBlankValueClearsTheField() throws Exception {
        User sa = superAdmin();
        String code = unicCode();
        String body = mockMvc.perform(post("/api/v1/platform/organizations")
                        .header(AUTHORIZATION, bearer(tokenFor(sa)))
                        .contentType(JSON)
                        .content("{\"code\":\"" + code + "\",\"name\":\"Clear Me\",\"contactPhone\":\"555\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(JsonPath.read(body, "$.data.id"));

        // This is how an operator removes a stale phone number.
        mockMvc.perform(patch("/api/v1/platform/organizations/" + id)
                        .header(AUTHORIZATION, bearer(tokenFor(sa)))
                        .contentType(JSON)
                        .content("{\"contactPhone\":\"\"}"))
                .andExpect(status().isOk());
        assertThat(organizationRepository.findById(id).orElseThrow().getContactPhone()).isNull();
    }

    @Test
    void aBlankNameIsRefused() throws Exception {
        User sa = superAdmin();
        UUID id = createOrganization(sa, unicCode(), "Has A Name");

        // name is NOT NULL in the schema, so "clear it" is not a meaningful operation.
        mockMvc.perform(patch("/api/v1/platform/organizations/" + id)
                        .header(AUTHORIZATION, bearer(tokenFor(sa)))
                        .contentType(JSON)
                        .content("{\"name\":\"   \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void theCodeAndStatusCannotBeChangedThroughTheProfile() throws Exception {
        User sa = superAdmin();
        String code = unicCode();
        UUID id = createOrganization(sa, code, "Stable Identity");

        // code, status and primacy each have their own endpoint with their own permission. A profile
        // edit that silently changed one of them would be an authority bypass wearing a form.
        //
        // The request is REJECTED rather than partially applied: application.yml sets
        // `fail-on-unknown-properties: true`, so a payload carrying fields this endpoint does not own
        // is a 400, not a silent subset. That is the stronger guarantee — an operator who sends
        // "status" here gets told the field is wrong instead of believing it took effect.
        mockMvc.perform(patch("/api/v1/platform/organizations/" + id)
                        .header(AUTHORIZATION, bearer(tokenFor(sa)))
                        .contentType(JSON)
                        .content("{\"code\":\"HACKED\",\"status\":\"INACTIVE\",\"name\":\"Renamed\"}"))
                .andExpect(status().isBadRequest());

        Organization saved = organizationRepository.findById(id).orElseThrow();
        assertThat(saved.getCode()).as("code is immutable through this endpoint").isEqualTo(code);
        assertThat(saved.getStatus()).as("status is owned by §72.16").isEqualTo(Status.ACTIVE);
        assertThat(saved.getName()).as("the rejected request changed nothing").isEqualTo("Stable Identity");
    }

    @Test
    void anUnknownFieldIsRejectedRatherThanIgnored() throws Exception {
        User sa = superAdmin();
        UUID id = createOrganization(sa, unicCode(), "Strict Payload");

        // A typo in a field name must fail loudly. Silently ignoring "cityy" would leave the operator
        // believing a value was saved when it was discarded.
        mockMvc.perform(patch("/api/v1/platform/organizations/" + id)
                        .header(AUTHORIZATION, bearer(tokenFor(sa)))
                        .contentType(JSON)
                        .content("{\"cityy\":\"Nowhere\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void thePlatformTenantCannotBeEdited() throws Exception {
        User sa = superAdmin();
        Organization platform = organizationRepository.findByCode("PLATFORM").orElseThrow();

        mockMvc.perform(patch("/api/v1/platform/organizations/" + platform.getId())
                        .header(AUTHORIZATION, bearer(tokenFor(sa)))
                        .contentType(JSON)
                        .content("{\"name\":\"Hijacked\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void anOrganizationAdminCannotEditThroughThePlatformSurface() throws Exception {
        User sa = superAdmin();
        UUID id = createOrganization(sa, unicCode(), "Not Yours");
        User admin = createUserInOrganization(id, syntheticEmail("profile-admin"), PASSWORD,
                Status.ACTIVE, "ADMIN");

        // The tenant ADMIN does have a profile write — but through /admin/organization-config/profile
        // for their OWN organization. The platform surface must stay platform-only.
        mockMvc.perform(patch("/api/v1/platform/organizations/" + id)
                        .header(AUTHORIZATION, bearer(tokenFor(admin)))
                        .contentType(JSON)
                        .content("{\"name\":\"Self Served\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void anEditIsAudited() throws Exception {
        User sa = superAdmin();
        UUID id = createOrganization(sa, unicCode(), "Audited Org");

        mockMvc.perform(patch("/api/v1/platform/organizations/" + id)
                        .header(AUTHORIZATION, bearer(tokenFor(sa)))
                        .contentType(JSON)
                        .content("{\"city\":\"Chennai\"}"))
                .andExpect(status().isOk());

        // AGENTS.md rule 11: the audit row is written transactionally with the mutation.
        assertThat(auditLogRepository.findAll().stream()
                .anyMatch(entry -> "ORGANIZATION_UPDATE".equals(entry.getAction()))).isTrue();
    }

    private UUID createOrganization(User sa, String code, String name) throws Exception {
        String body = mockMvc.perform(post("/api/v1/platform/organizations")
                        .header(AUTHORIZATION, bearer(tokenFor(sa)))
                        .contentType(JSON)
                        .content("{\"code\":\"" + code + "\",\"name\":\"" + name + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(body, "$.data.id"));
    }
}
