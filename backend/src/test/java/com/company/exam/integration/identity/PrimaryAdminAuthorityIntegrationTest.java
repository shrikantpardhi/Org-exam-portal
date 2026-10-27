package com.company.exam.integration.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.exam.common.enums.Status;
import com.company.exam.identity.entity.Organization;
import com.company.exam.identity.entity.Role;
import com.company.exam.identity.entity.User;
import com.company.exam.identity.repository.OrganizationRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

/**
 * The primary-administrator rule (ADR-0052): only an organization's designated primary
 * administrator may change another user's roles, and an organization with no designated primary
 * refuses role editing entirely (fail-closed).
 *
 * <p>These assertions matter because the rule is a *restriction*: the failure mode of a missing
 * restricted path is silent over-permission, which no happy-path test would notice.
 */
class PrimaryAdminAuthorityIntegrationTest extends AbstractIdentityIntegrationTest {

    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;
    private static final String PASSWORD = "Super-Strong-Pass-1";

    @Autowired
    private OrganizationRepository organizationRepository;

    /** A fresh organization plus a specified primary. */
    private Organization newOrganization(String prefix, String primaryEmail, UUID primaryId) {
        Organization organization = new Organization();
        organization.setCode((prefix + "-" + UUID.randomUUID()).toUpperCase().substring(0, 24));
        organization.setName(prefix);
        organization.setStatus(Status.ACTIVE);
        organization.setPrimaryAdminUserId(primaryId);
        return organizationRepository.save(organization);
    }

    private String rolesBody(String... codes) {
        StringBuilder body = new StringBuilder("{\"roles\":[");
        for (int index = 0; index < codes.length; index++) {
            if (index > 0) {
                body.append(',');
            }
            body.append('"').append(role(codes[index]).getId()).append('"');
        }
        return body.append("]}").toString();
    }

    @Test
    void aNonPrimaryAdministratorCannotChangeAnotherUsersRoles() throws Exception {
        // The primary is an administrator of the organization; the actor is a second administrator.
        Organization organization = newOrganization("primary-guard", null, null);
        User primary = createUserInOrganization(organization.getId(), syntheticEmail("guard-primary"),
                PASSWORD, Status.ACTIVE, "ADMIN");
        organization.setPrimaryAdminUserId(primary.getId());
        organizationRepository.save(organization);
        User secondary = createUserInOrganization(organization.getId(), syntheticEmail("guard-secondary"),
                PASSWORD, Status.ACTIVE, "ADMIN");
        User target = createUserInOrganization(organization.getId(), syntheticEmail("guard-target"),
                PASSWORD, Status.ACTIVE, "STUDENT");

        mockMvc.perform(put("/api/v1/users/" + target.getId())
                        .header(AUTHORIZATION, bearer(tokenFor(secondary)))
                        .contentType(JSON)
                        .content(rolesBody("STAFF")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errors[0].code").value("PRIMARY_ADMIN_REQUIRED"));
    }

    @Test
    void thePrimaryAdministratorMayChangeAnotherUsersRoles() throws Exception {
        Organization organization = newOrganization("primary-allow", null, null);
        User primary = createUserInOrganization(organization.getId(), syntheticEmail("allow-primary"),
                PASSWORD, Status.ACTIVE, "ADMIN");
        organization.setPrimaryAdminUserId(primary.getId());
        organizationRepository.save(organization);
        User target = createUserInOrganization(organization.getId(), syntheticEmail("allow-target"),
                PASSWORD, Status.ACTIVE, "STUDENT");

        // The primary holds ADMIN_ROLE_ASSIGN, so the assign itself is permitted; the assertion is
        // that the primary-admin guard does not stand in the way.
        mockMvc.perform(put("/api/v1/users/" + target.getId())
                        .header(AUTHORIZATION, bearer(tokenFor(primary)))
                        .contentType(JSON)
                        .content(rolesBody("STUDENT", "STAFF")))
                .andExpect(status().isOk());
    }

    @Test
    void anOrganizationWithNoPrimaryRefusesRoleEditing() throws Exception {
        // Fail-closed: NULL primary means nobody may edit roles. This is the state ADR-0052 chose
        // deliberately over guessing an owner from created_on.
        Organization organization = newOrganization("primary-none", null, null);
        User admin = createUserInOrganization(organization.getId(), syntheticEmail("none-admin"),
                PASSWORD, Status.ACTIVE, "ADMIN");
        User target = createUserInOrganization(organization.getId(), syntheticEmail("none-target"),
                PASSWORD, Status.ACTIVE, "STUDENT");

        mockMvc.perform(put("/api/v1/users/" + target.getId())
                        .header(AUTHORIZATION, bearer(tokenFor(admin)))
                        .contentType(JSON)
                        .content(rolesBody("STAFF")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errors[0].code").value("PRIMARY_ADMIN_REQUIRED"));
    }

    @Test
    void thePrimaryAdministratorStillCannotChangeTheirOwnRoles() throws Exception {
        // The primary-admin rule widens nothing: SELF_ROLE_MODIFICATION keeps precedence, so being
        // primary is authority over others, never over oneself.
        Organization organization = newOrganization("primary-self", null, null);
        User primary = createUserInOrganization(organization.getId(), syntheticEmail("self-primary"),
                PASSWORD, Status.ACTIVE, "ADMIN");
        organization.setPrimaryAdminUserId(primary.getId());
        organizationRepository.save(organization);

        mockMvc.perform(put("/api/v1/users/" + primary.getId())
                        .header(AUTHORIZATION, bearer(tokenFor(primary)))
                        .contentType(JSON)
                        .content(rolesBody("STUDENT")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errors[0].code").value("SELF_ROLE_MODIFICATION"));
    }

    @Test
    void theColumnIsNullableAndUnsetByDefault() {
        // No backfill: a pre-existing organization must stay valid with no primary (ADR-0052).
        Organization organization = newOrganization("primary-null", null, null);
        assertThat(organization.getPrimaryAdminUserId()).isNull();
        Organization reloaded = organizationRepository.findById(organization.getId()).orElseThrow();
        assertThat(reloaded.getPrimaryAdminUserId()).isNull();
    }
}
