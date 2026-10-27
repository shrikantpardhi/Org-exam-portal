package com.company.exam.integration.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.exam.common.enums.Status;
import com.company.exam.identity.entity.Role;
import com.company.exam.identity.entity.User;
import com.company.exam.identity.entity.UserRole;
import com.company.exam.integration.identity.AbstractIdentityIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * Archive / un-archive and primacy transfer for organization administrators (ADR-0054,
 * {@code specs/05} §72.14-§72.15).
 *
 * <p>Three things are asserted that a happy-path test would miss:
 *
 * <ol>
 *   <li><b>Archiving keeps the {@code user_role} ACTIVE</b> and still frees a cap slot. Both halves
 *       matter: dropping the role would make restore impossible without a snapshot, and not
 *       freeing the slot would make an organization unable to replace a departed administrator.
 *   <li><b>Archiving revokes access.</b> Authorization reads a cached permission set and does not
 *       re-check user status per request, so the session expiry and cache eviction are load-
 *       bearing, not decoration.
 *   <li><b>Primacy transfer is the platform owner's, and it is idempotent.</b> The transfer exists
 *       because ADR-0052 otherwise made the designation permanent (GAP-197).
 * </ol>
 *
 * <p>Scoped to a fresh organization per test: the database is shared and not rolled back, and these
 * assertions are about one organization's administrator set.
 */
class PlatformAdminArchiveAndPrimaryIntegrationTest extends AbstractIdentityIntegrationTest {

    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;
    private static final String PASSWORD = "Super-Strong-Pass-1";

    private User superAdmin() {
        return createUser(syntheticEmail("arch-super"), PASSWORD, "SUPER_ADMIN");
    }

    private UUID newOrganization(User sa, String prefix) throws Exception {
        String code = (prefix + "-" + UUID.randomUUID()).toString().toUpperCase().substring(0, 24);
        String body = mockMvc.perform(post("/api/v1/platform/organizations")
                        .header(AUTHORIZATION, bearer(tokenFor(sa)))
                        .contentType(JSON)
                        .content("{\"code\":\"" + code + "\",\"name\":\"" + code + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(body, "$.data.id"));
    }

    private String createAdmin(User sa, UUID orgId, String email, boolean makePrimary) throws Exception {
        return mockMvc.perform(post("/api/v1/platform/organizations/" + orgId + "/admins")
                        .header(AUTHORIZATION, bearer(tokenFor(sa)))
                        .contentType(JSON)
                        .content("{\"firstName\":\"Org\",\"lastName\":\"Admin\",\"email\":\""
                                + email + "\",\"makePrimary\":" + makePrimary + "}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private List<String> adminEmails(User sa, UUID orgId, String statusParam) throws Exception {
        String url = "/api/v1/platform/organizations/" + orgId + "/admins";
        if (statusParam != null) {
            url = url + "?status=" + statusParam;
        }
        String body = mockMvc.perform(get(url).header(AUTHORIZATION, bearer(tokenFor(sa))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data[*].email");
    }

    // ---------------------------------------------------------------- create with primary

    @Test
    void creatingAnAdminWithMakePrimaryDesignatesIt() throws Exception {
        User sa = superAdmin();
        UUID orgId = newOrganization(sa, "mkprim");
        String email = syntheticEmail("mkprim-admin");

        createAdmin(sa, orgId, email, true);

        mockMvc.perform(get("/api/v1/platform/organizations/" + orgId)
                        .header(AUTHORIZATION, bearer(tokenFor(sa))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.primaryAdminEmail").value(email));
    }

    @Test
    void theFirstAdminBecomesPrimaryEvenWithoutMakePrimary() throws Exception {
        User sa = superAdmin();
        UUID orgId = newOrganization(sa, "implicit");
        String email = syntheticEmail("implicit-admin");

        createAdmin(sa, orgId, email, false);

        // The escape from ADR-0052 fail-closed: an organization with no primary would otherwise
        // never be able to edit roles, and no screen offers a way out.
        mockMvc.perform(get("/api/v1/platform/organizations/" + orgId)
                        .header(AUTHORIZATION, bearer(tokenFor(sa))))
                .andExpect(jsonPath("$.data.primaryAdminEmail").value(email));
    }

    @Test
    void aLaterAdminDoesNotStealPrimacyImplicitly() throws Exception {
        User sa = superAdmin();
        UUID orgId = newOrganization(sa, "nosteal");
        String first = syntheticEmail("nosteal-first");
        createAdmin(sa, orgId, first, false);
        createAdmin(sa, orgId, syntheticEmail("nosteal-second"), false);

        mockMvc.perform(get("/api/v1/platform/organizations/" + orgId)
                        .header(AUTHORIZATION, bearer(tokenFor(sa))))
                .andExpect(jsonPath("$.data.primaryAdminEmail").value(first));
    }

    // ---------------------------------------------------------------- primacy transfer

    @Test
    void thePrimaryCanBeTransferredAndIsIdempotent() throws Exception {
        User sa = superAdmin();
        UUID orgId = newOrganization(sa, "xfer");
        String first = syntheticEmail("xfer-first");
        String second = syntheticEmail("xfer-second");
        String firstBody = createAdmin(sa, orgId, first, false);
        String secondBody = createAdmin(sa, orgId, second, false);
        String secondId = JsonPath.read(secondBody, "$.data.userId");
        String firstId = JsonPath.read(firstBody, "$.data.userId");

        mockMvc.perform(patch("/api/v1/platform/organizations/" + orgId + "/primary-admin")
                        .header(AUTHORIZATION, bearer(tokenFor(sa)))
                        .contentType(JSON)
                        .content("{\"userId\":\"" + secondId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.primaryAdminEmail").value(second));

        // Re-designating the current primary is what a retry looks like: a success, not an error.
        mockMvc.perform(patch("/api/v1/platform/organizations/" + orgId + "/primary-admin")
                        .header(AUTHORIZATION, bearer(tokenFor(sa)))
                        .contentType(JSON)
                        .content("{\"userId\":\"" + secondId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.primaryAdminEmail").value(second));

        // And the previous primary is now an ordinary administrator.
        String body = mockMvc.perform(get("/api/v1/platform/organizations/" + orgId + "/admins")
                        .header(AUTHORIZATION, bearer(tokenFor(sa))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<Boolean> flags = JsonPath.read(body, "$.data[*].isPrimary");
        assertThat(flags).containsExactlyInAnyOrder(true, false);
        // The transferred-to account is the one flagged primary; the old primary is now ordinary.
        List<String> primaryEmails = JsonPath.read(body, "$.data[?(@.isPrimary == true)].email");
        assertThat(primaryEmails).containsExactly(second);
        assertThat(firstId).isNotEqualTo(secondId);
    }

    @Test
    void aUserFromAnotherOrganizationCannotBeMadePrimary() throws Exception {
        User sa = superAdmin();
        UUID orgA = newOrganization(sa, "xorg-a");
        UUID orgB = newOrganization(sa, "xorg-b");
        String otherBody = createAdmin(sa, orgA, syntheticEmail("xorg-admin"), false);
        String otherId = JsonPath.read(otherBody, "$.data.userId");

        // 404, never 403: a forbidden response would confirm the id exists elsewhere (§72.13).
        mockMvc.perform(patch("/api/v1/platform/organizations/" + orgB + "/primary-admin")
                        .header(AUTHORIZATION, bearer(tokenFor(sa)))
                        .contentType(JSON)
                        .content("{\"userId\":\"" + otherId + "\"}"))
                .andExpect(status().isNotFound());
    }

    // ---------------------------------------------------------------- archive / restore

    @Test
    void archivingKeepsTheAdminRoleButRemovesTheAccountFromTheDefaultList() throws Exception {
        User sa = superAdmin();
        UUID orgId = newOrganization(sa, "arch");
        String primary = syntheticEmail("arch-primary");
        String second = syntheticEmail("arch-second");
        createAdmin(sa, orgId, primary, false);
        String secondBody = createAdmin(sa, orgId, second, false);
        String secondId = JsonPath.read(secondBody, "$.data.userId");

        mockMvc.perform(delete("/api/v1/platform/organizations/" + orgId + "/admins/" + secondId)
                        .header(AUTHORIZATION, bearer(tokenFor(sa))))
                .andExpect(status().isOk());

        // Gone from the default (currently-serving) list ...
        assertThat(adminEmails(sa, orgId, null)).containsExactly(primary);
        // ... but still visible when explicitly asked for, which is what makes restore possible.
        assertThat(adminEmails(sa, orgId, "ARCHIVED")).containsExactly(second);

        // The role row survives, so restore does not need a snapshot (ADR-0054).
        Role adminRole = role("ADMIN");
        assertThat(userRoleRepository.findByUserIdAndRoleId(UUID.fromString(secondId), adminRole.getId()))
                .as("archiving must not drop the role assignment")
                .isPresent()
                .get()
                .extracting(UserRole::getStatus)
                .isEqualTo(Status.ACTIVE);

        assertThat(userRepository.findById(UUID.fromString(secondId)))
                .get()
                .extracting(User::getStatus)
                .isEqualTo(Status.ARCHIVED);
    }

    @Test
    void anArchivedAdminLosesEveryPermission() throws Exception {
        User sa = superAdmin();
        UUID orgId = newOrganization(sa, "archperm");
        String primary = syntheticEmail("archperm-primary");
        createAdmin(sa, orgId, primary, false);

        // An administrator created through the platform endpoint starts with must_change_password,
        // so assert on permissions rather than logging in: the permission query filters user status,
        // and that is the mechanism under test.
        String adminEmail = syntheticEmail("archperm-target");
        String adminBody = createAdmin(sa, orgId, adminEmail, false);
        String adminId = JsonPath.read(adminBody, "$.data.userId");

        assertThat(authorizationService.effectivePermissions(UUID.fromString(adminId)))
                .as("an active administrator holds permissions")
                .isNotEmpty();

        mockMvc.perform(delete("/api/v1/platform/organizations/" + orgId + "/admins/" + adminId)
                        .header(AUTHORIZATION, bearer(tokenFor(sa))))
                .andExpect(status().isOk());

        // findEffectivePermissionCodes requires user.status = ACTIVE, so an archived account resolves to
        // no permissions at all even though its role assignment is still present. The evict is what
        // makes the very next read observe this rather than a stale cached set.
        authorizationService.evictAfterCommit(UUID.fromString(adminId));
        assertThat(authorizationService.effectivePermissions(UUID.fromString(adminId)))
                .as("an archived account resolves to no permissions")
                .isEmpty();
    }

    @Test
    void anArchivedAdminCanBeRestored() throws Exception {
        User sa = superAdmin();
        UUID orgId = newOrganization(sa, "restore");
        String primary = syntheticEmail("restore-primary");
        String second = syntheticEmail("restore-second");
        createAdmin(sa, orgId, primary, false);
        String secondBody = createAdmin(sa, orgId, second, false);
        String secondId = JsonPath.read(secondBody, "$.data.userId");

        mockMvc.perform(delete("/api/v1/platform/organizations/" + orgId + "/admins/" + secondId)
                        .header(AUTHORIZATION, bearer(tokenFor(sa))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/platform/organizations/" + orgId + "/admins/" + secondId + "/restore")
                        .header(AUTHORIZATION, bearer(tokenFor(sa))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(second))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.isPrimary").value(false));

        // Back in the default list, and the account is ACTIVE again.
        assertThat(adminEmails(sa, orgId, null)).contains(primary, second);
        assertThat(userRepository.findById(UUID.fromString(secondId)))
                .get()
                .extracting(User::getStatus)
                .isEqualTo(Status.ACTIVE);
    }

    @Test
    void restoringAnAccountThatIsNotArchivedIsRefused() throws Exception {
        User sa = superAdmin();
        UUID orgId = newOrganization(sa, "notarch");
        createAdmin(sa, orgId, syntheticEmail("notarch-primary"), false);
        String activeBody = createAdmin(sa, orgId, syntheticEmail("notarch-second"), false);
        String activeId = JsonPath.read(activeBody, "$.data.userId");

        // Restore is a recovery action; running it on a live account would hide an operator mistake.
        mockMvc.perform(post("/api/v1/platform/organizations/" + orgId + "/admins/" + activeId + "/restore")
                        .header(AUTHORIZATION, bearer(tokenFor(sa))))
                .andExpect(status().isConflict());
    }

    @Test
    void archivingFreesACapSlot() throws Exception {
        User sa = superAdmin();
        UUID orgId = newOrganization(sa, "slot");
        String primary = syntheticEmail("slot-primary");
        createAdmin(sa, orgId, primary, false);

        // At the cap (5), adding another administrator is refused ...
        for (int index = 0; index < 4; index++) {
            createAdmin(sa, orgId, syntheticEmail("slot-fill-" + index), false);
        }
        mockMvc.perform(post("/api/v1/platform/organizations/" + orgId + "/admins")
                        .header(AUTHORIZATION, bearer(tokenFor(sa)))
                        .contentType(JSON)
                        .content("{\"firstName\":\"Over\",\"lastName\":\"Cap\",\"email\":\""
                                + syntheticEmail("slot-over") + "\"}"))
                .andExpect(status().isConflict());

        // ... and archiving one non-primary administrator frees exactly one slot.
        String body = mockMvc.perform(get("/api/v1/platform/organizations/" + orgId + "/admins")
                        .header(AUTHORIZATION, bearer(tokenFor(sa))))
                .andReturn().getResponse().getContentAsString();
        List<String> ids = JsonPath.read(body, "$.data[?(@.isPrimary == false)].userId");
        assertThat(ids).isNotEmpty();
        mockMvc.perform(delete("/api/v1/platform/organizations/" + orgId + "/admins/" + ids.get(0))
                        .header(AUTHORIZATION, bearer(tokenFor(sa))))
                .andExpect(status().isOk());

        createAdmin(sa, orgId, syntheticEmail("slot-replacement"), false);
    }

    @Test
    void thePrimaryAndTheLastAdminCannotBeArchived() throws Exception {
        User sa = superAdmin();
        UUID orgId = newOrganization(sa, "guards");
        String primaryBody = createAdmin(sa, orgId, syntheticEmail("guards-primary"), false);
        String primaryId = JsonPath.read(primaryBody, "$.data.userId");

        // The primary cannot be archived: nobody could then edit roles.
        mockMvc.perform(delete("/api/v1/platform/organizations/" + orgId + "/admins/" + primaryId)
                        .header(AUTHORIZATION, bearer(tokenFor(sa))))
                .andExpect(status().isConflict());

        // A sole administrator cannot be archived either (here it is the same account).
        mockMvc.perform(delete("/api/v1/platform/organizations/" + orgId + "/admins/" + primaryId)
                        .header(AUTHORIZATION, bearer(tokenFor(sa))))
                .andExpect(status().isConflict());
    }

    @Test
    void anArchivedAdminCannotBeMadePrimary() throws Exception {
        User sa = superAdmin();
        UUID orgId = newOrganization(sa, "archprim");
        createAdmin(sa, orgId, syntheticEmail("archprim-primary"), false);
        String secondBody = createAdmin(sa, orgId, syntheticEmail("archprim-second"), false);
        String secondId = JsonPath.read(secondBody, "$.data.userId");

        mockMvc.perform(delete("/api/v1/platform/organizations/" + orgId + "/admins/" + secondId)
                .header(AUTHORIZATION, bearer(tokenFor(sa)))).andExpect(status().isOk());

        // Designating an archived account would point role-editing authority at an account that
        // cannot log in, locking the organization out of its own administration.
        mockMvc.perform(patch("/api/v1/platform/organizations/" + orgId + "/primary-admin")
                        .header(AUTHORIZATION, bearer(tokenFor(sa)))
                        .contentType(JSON)
                        .content("{\"userId\":\"" + secondId + "\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void anArchivedAdminCannotHaveItsRolesEdited() throws Exception {
        User sa = superAdmin();
        UUID orgId = newOrganization(sa, "archroles");
        createAdmin(sa, orgId, syntheticEmail("archroles-primary"), false);
        String secondBody = createAdmin(sa, orgId, syntheticEmail("archroles-second"), false);
        String secondId = JsonPath.read(secondBody, "$.data.userId");

        mockMvc.perform(delete("/api/v1/platform/organizations/" + orgId + "/admins/" + secondId)
                .header(AUTHORIZATION, bearer(tokenFor(sa)))).andExpect(status().isOk());

        mockMvc.perform(patch("/api/v1/platform/organizations/" + orgId + "/admins/" + secondId + "/roles")
                        .header(AUTHORIZATION, bearer(tokenFor(sa)))
                        .contentType(JSON)
                        .content("{\"roles\":[\"STUDENT\"]}"))
                .andExpect(status().isConflict());
    }

    @Test
    void anArchivedAdminCanStillHaveRolesSetAfterRestore() throws Exception {
        User sa = superAdmin();
        UUID orgId = newOrganization(sa, "postrestore");
        createAdmin(sa, orgId, syntheticEmail("postrestore-primary"), false);
        String secondBody = createAdmin(sa, orgId, syntheticEmail("postrestore-second"), false);
        String secondId = JsonPath.read(secondBody, "$.data.userId");

        mockMvc.perform(delete("/api/v1/platform/organizations/" + orgId + "/admins/" + secondId)
                .header(AUTHORIZATION, bearer(tokenFor(sa)))).andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/platform/organizations/" + orgId + "/admins/" + secondId + "/restore")
                .header(AUTHORIZATION, bearer(tokenFor(sa)))).andExpect(status().isOk());

        mockMvc.perform(patch("/api/v1/platform/organizations/" + orgId + "/admins/" + secondId + "/roles")
                        .header(AUTHORIZATION, bearer(tokenFor(sa)))
                        .contentType(JSON)
                        .content("{\"roles\":[\"STUDENT\",\"STAFF\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roles.length()").value(2));
    }

    @Test
    void anArchivedAdminDoesNotCountTowardTheOrgAdminCount() throws Exception {
        User sa = superAdmin();
        UUID orgId = newOrganization(sa, "count");
        createAdmin(sa, orgId, syntheticEmail("count-primary"), false);
        String secondBody = createAdmin(sa, orgId, syntheticEmail("count-second"), false);
        String secondId = JsonPath.read(secondBody, "$.data.userId");

        mockMvc.perform(get("/api/v1/platform/organizations/" + orgId)
                        .header(AUTHORIZATION, bearer(tokenFor(sa))))
                .andExpect(jsonPath("$.data.adminCount").value(2));

        mockMvc.perform(delete("/api/v1/platform/organizations/" + orgId + "/admins/" + secondId)
                .header(AUTHORIZATION, bearer(tokenFor(sa)))).andExpect(status().isOk());

        // adminCount means "currently serving": an archived administrator is history, not a count.
        mockMvc.perform(get("/api/v1/platform/organizations/" + orgId)
                        .header(AUTHORIZATION, bearer(tokenFor(sa))))
                .andExpect(jsonPath("$.data.adminCount").value(1));
    }

    @Test
    void rolesAreReplacedAsAWholeSet() throws Exception {
        User sa = superAdmin();
        UUID orgId = newOrganization(sa, "roleset");
        createAdmin(sa, orgId, syntheticEmail("roleset-primary"), false);
        String body = createAdmin(sa, orgId, syntheticEmail("roleset-target"), false);
        String userId = JsonPath.read(body, "$.data.userId");

        mockMvc.perform(patch("/api/v1/platform/organizations/" + orgId + "/admins/" + userId + "/roles")
                        .header(AUTHORIZATION, bearer(tokenFor(sa)))
                        .contentType(JSON)
                        .content("{\"roles\":[\"STUDENT\",\"STAFF\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roles.length()").value(2));

        // A second call replaces the set rather than adding to it.
        mockMvc.perform(patch("/api/v1/platform/organizations/" + orgId + "/admins/" + userId + "/roles")
                        .header(AUTHORIZATION, bearer(tokenFor(sa)))
                        .contentType(JSON)
                        .content("{\"roles\":[\"STUDENT\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roles.length()").value(1))
                .andExpect(jsonPath("$.data.roles[0]").value("STUDENT"));

        // And an empty list leaves the administrator with no extra roles, which is valid.
        mockMvc.perform(patch("/api/v1/platform/organizations/" + orgId + "/admins/" + userId + "/roles")
                        .header(AUTHORIZATION, bearer(tokenFor(sa)))
                        .contentType(JSON)
                        .content("{\"roles\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roles.length()").value(0));
    }
}
