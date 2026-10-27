package com.company.exam.integration.administration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.exam.administration.service.GlobalSearchService;
import com.company.exam.administration.service.SupportConsoleService;
import com.company.exam.identity.entity.User;
import com.company.exam.integration.evaluation.AbstractEvaluationIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * P11 administration end-to-end: configuration, rules, lookups, the durable audit trail, global
 * search permission filtering, health secrecy, and support scope masking
 * ({@code specs/20-P11-Administration-V1.5.md §8-§11, §37-§40, §55-§62, §34-§36, §82, §83}).
 */
class P11AdministrationIntegrationTest extends AbstractEvaluationIntegrationTest {

    /**
     * A SUPER_ADMIN token. The installation owner is created through the same helper as any other
     * user; what matters is the role code, which is what the authorization check reads.
     */
    private String superAdminToken() {
        return tokenFor(createUser(syntheticEmail("p11-super-admin"), PASSWORD, "SUPER_ADMIN"));
    }

    @Autowired
    private GlobalSearchService globalSearchService;
    @Autowired
    private SupportConsoleService supportConsoleService;

    // ------------------------------------------------------------------ configuration

    @Test
    void settingsAreValidatedAgainstTheRegistryAndAudited() throws Exception {
        String admin = adminToken();

        mockMvc.perform(get("/api/v1/organization-settings").header(AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.registry.length()").value(5));

        // An unregistered key is rejected; a syntactically valid document is not necessarily valid (§8.4).
        mockMvc.perform(put("/api/v1/organization-settings").header(AUTHORIZATION, bearer(admin))
                        .contentType(JSON).content("{\"configuration\":{\"invented.key\":true}}"))
                .andExpect(status().isBadRequest());

        // A cross-field rule is enforced: maintenance mode requires a message.
        mockMvc.perform(put("/api/v1/organization-settings").header(AUTHORIZATION, bearer(admin))
                        .contentType(JSON).content("{\"configuration\":{\"operations.maintenanceMode\":true}}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(put("/api/v1/organization-settings").header(AUTHORIZATION, bearer(admin))
                        .contentType(JSON)
                        .content("{\"configuration\":{\"operations.maintenanceMode\":true,"
                                + "\"operations.maintenanceMessage\":\"Upgrading\","
                                + "\"notifications.emailEnabled\":false}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.configuration['operations.maintenanceMode']").value(true))
                .andExpect(jsonPath("$.data.configuration['display.timeZone']").value("UTC"));

        // The update is audited into the durable trail (§11.11).
        Long audited = jdbcTemplate.queryForObject(
                "select count(*) from audit_log where entity_name = 'OrganizationSetting' and action = 'UPDATE'",
                Long.class);
        assertThat(audited).isGreaterThanOrEqualTo(1L);
    }

    @Test
    void businessRulesAreDataOnlyAndCodeIsUnique() throws Exception {
        // V40 (2026-10-26): rule authoring moved to the installation owner, because a business rule
        // expresses platform policy and the organization administrator is subject to it, not its
        // author. This test is about the data-only contract of a rule, so it drives the endpoint as
        // the account that may actually write one; the refusal below is asserted separately.
        String admin = superAdminToken();

        // A rule is never a hidden scripting engine (§9.3).
        mockMvc.perform(post("/api/v1/business-rules").header(AUTHORIZATION, bearer(admin))
                        .contentType(JSON)
                        .content("{\"code\":\"SCORING_DEFAULTS\",\"configuration\":{\"script\":\"return 1\"}}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/business-rules").header(AUTHORIZATION, bearer(admin))
                        .contentType(JSON)
                        .content("{\"code\":\"P11_RULE_A\",\"configuration\":{\"rounding\":\"HALF_UP\"}}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.code").value("P11_RULE_A"));

        mockMvc.perform(post("/api/v1/business-rules").header(AUTHORIZATION, bearer(admin))
                        .contentType(JSON)
                        .content("{\"code\":\"P11_RULE_A\",\"configuration\":{\"rounding\":\"DOWN\"}}"))
                .andExpect(status().isConflict());

        // DELETE is not frozen (§9.5).
        String listed = mockMvc.perform(get("/api/v1/business-rules").header(AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(listed).contains("P11_RULE_A");
    }

    @Test
    void lookupDeleteIsConservative() throws Exception {
        String admin = adminToken();
        String created = mockMvc.perform(post("/api/v1/lookups").header(AUTHORIZATION, bearer(admin))
                        .contentType(JSON)
                        .content("{\"category\":\"P11_CATEGORY\",\"code\":\"ONE\",\"name\":\"First\","
                                + "\"displayOrder\":10}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String lookupId = JsonPath.read(created, "$.data.id");

        // An ACTIVE value that consumers may reference is archived, not physically deleted (§10.4).
        mockMvc.perform(delete("/api/v1/lookups/" + lookupId).header(AUTHORIZATION, bearer(admin)))
                .andExpect(status().is(204));

        String remaining = jdbcTemplate.queryForObject(
                "select status from lookup where id = ?", String.class, UUID.fromString(lookupId));
        assertThat(remaining).isEqualTo("ARCHIVED");
    }

    // ------------------------------------------------------------------ audit

    @Test
    void auditTrailIsDurableSearchableAndReadOnly() throws Exception {
        String admin = adminToken();
        // A mutation through an owning service produces audit evidence in the same transaction (§11.11).
        mockMvc.perform(post("/api/v1/lookups").header(AUTHORIZATION, bearer(admin)).contentType(JSON)
                        .content("{\"category\":\"P11_AUDIT\",\"code\":\"X\",\"name\":\"Audited\"}"))
                .andExpect(status().isCreated());

        String trail = mockMvc.perform(get("/api/v1/audit-logs?entityName=Lookup&action=CREATE")
                        .header(AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(trail).contains("P11_AUDIT");

        // The trail is insert-only: no update or delete endpoint exists at all (§11.2).
        mockMvc.perform(delete("/api/v1/audit-logs/" + UUID.randomUUID()).header(AUTHORIZATION, bearer(admin)))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/api/v1/audit-logs/" + UUID.randomUUID()).header(AUTHORIZATION, bearer(admin))
                        .contentType(JSON).content("{}"))
                .andExpect(status().isNotFound());

        // Students receive no audit access (§11.9).
        User student = newStudent("p11-audit-student");
        mockMvc.perform(get("/api/v1/audit-logs").header(AUTHORIZATION, bearer(tokenFor(student))))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------ search

    @Test
    void searchRequiresPermissionAndOmitUnpermittedResources() throws Exception {
        User student = newStudent("p11-search-student");
        mockMvc.perform(get("/api/v1/search?q=test").header(AUTHORIZATION, bearer(tokenFor(student))))
                .andExpect(status().isForbidden());

        // Without the resource permission the resource is omitted, not filtered after the fact (§39, §40).
        var denied = globalSearchService.search("audit", "AUDIT_LOG", 10, null, (auth, permission) -> false);
        assertThat(denied.searchedResources()).isEmpty();
        assertThat(denied.results()).isEmpty();

        var permitted = globalSearchService.search("Lookup", "AUDIT_LOG", 10, null, (auth, permission) -> true);
        assertThat(permitted.searchedResources()).containsExactly("AUDIT_LOG");

        String admin = adminToken();
        String resources = mockMvc.perform(get("/api/v1/search/resources").header(AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(resources).contains("USER").contains("AUDIT_LOG");
    }

    // ------------------------------------------------------------------ health

    @Test
    void healthExposesNoSecrets() throws Exception {
        String admin = adminToken();
        String body = mockMvc.perform(get("/api/v1/admin/system-health").header(AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.components.length()").value(8))
                .andReturn().getResponse().getContentAsString();

        // §58: never database passwords, the JWT secret, Redis credentials, provider secrets,
        // environment dumps, or raw stack traces.
        assertThat(body.toLowerCase(java.util.Locale.ROOT))
                .doesNotContain("password")
                .doesNotContain("secret")
                .doesNotContain("jdbc:")
                .doesNotContain("token")
                .doesNotContain("exception")
                .doesNotContain("stacktrace")
                .doesNotContain("at com.company");

        String overall = JsonPath.read(body, "$.data.overall");
        assertThat(overall).isIn("HEALTHY", "DEGRADED", "UNAVAILABLE");

        // A student cannot read system health.
        User student = newStudent("p11-health-student");
        mockMvc.perform(get("/api/v1/admin/system-health").header(AUTHORIZATION, bearer(tokenFor(student))))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------ support scope

    @Test
    void supportScopeMasksContactByDefault() {
        User student = newStudent("p11-support");
        String email = jdbcTemplate.queryForObject("select email from \"user\" where id = ?", String.class,
                student.getId());

        var masked = supportConsoleService.userView(student.getId(), false);
        assertThat(masked.contactUnmasked()).isFalse();
        assertThat(masked.email()).isNotEqualTo(email).contains("***");

        // Unmasking is only reachable with SUPPORT_USER_CONTACT (§35).
        var unmasked = supportConsoleService.userView(student.getId(), true);
        assertThat(unmasked.contactUnmasked()).isTrue();
        assertThat(unmasked.email()).isEqualTo(email);
    }

    @Test
    void maskingRulesFollowTheFrozenExamples() {
        assertThat(SupportConsoleService.maskEmail("john@example.com")).isEqualTo("j***@example.com");
        assertThat(SupportConsoleService.maskMobile("+91987651234")).isEqualTo("+91*****1234");
    }

    // ------------------------------------------------------------------ support actions

    @Test
    void supportNotificationGoesThroughTheOwningCommunicationService() throws Exception {
        User student = newStudent("p11-support-notify");
        // The support action delegates to Communication, which owns notification creation (§82, §83).
        supportConsoleService.sendNotification(student.getId(), "Support contact", "We replied to your query.",
                student.getId());

        Long notifications = jdbcTemplate.queryForObject(
                "select count(*) from notification where user_id = ? and title = 'Support contact'", Long.class,
                student.getId());
        assertThat(notifications).isEqualTo(1L);

        Long audited = jdbcTemplate.queryForObject(
                "select count(*) from audit_log where entity_name = 'User' and action = 'NOTIFY'", Long.class);
        assertThat(audited).isGreaterThanOrEqualTo(1L);
    }

    @Test
    void dashboardCardsComeFromOwningModules() throws Exception {
        String admin = adminToken();
        mockMvc.perform(get("/api/v1/admin/dashboard").header(AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sources.length()").value(5))
                .andExpect(jsonPath("$.data.activeUsers").isNumber());
    }

    /**
     * V40: the organization administrator reads the rules that govern it but cannot author them.
     *
     * <p>Asserted here rather than only in a migration test, because the migration proves the grant
     * was withdrawn while this proves the withdrawal actually refuses the HTTP call — the two can
     * diverge if a controller-level check were ever added or removed.
     */
    @Test
    void organizationAdminCanReadRulesButNotWriteThem() throws Exception {
        String admin = adminToken();

        mockMvc.perform(get("/api/v1/business-rules").header(AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/business-rules").header(AUTHORIZATION, bearer(admin))
                        .contentType(JSON)
                        .content("{\"code\":\"P11_ADMIN_CANNOT_WRITE\",\"configuration\":{\"x\":1}}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/v1/business-rules/" + UUID.randomUUID())
                        .header(AUTHORIZATION, bearer(admin))
                        .contentType(JSON)
                        .content("{\"configuration\":{\"x\":2}}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void administrationEndpointsArePermissionGated() throws Exception {
        User student = newStudent("p11-gate");
        for (String path : List.of("/api/v1/organization-settings", "/api/v1/business-rules", "/api/v1/lookups",
                "/api/v1/audit-logs", "/api/v1/admin/dashboard", "/api/v1/support/users/"
                        + UUID.randomUUID())) {
            mockMvc.perform(get(path).header(AUTHORIZATION, bearer(tokenFor(student))))
                    .andExpect(status().isForbidden());
        }
    }
}
