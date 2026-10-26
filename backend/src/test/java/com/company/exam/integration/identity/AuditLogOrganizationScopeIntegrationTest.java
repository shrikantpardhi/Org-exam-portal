package com.company.exam.integration.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.exam.common.audit.SecurityEventLogger;
import com.company.exam.common.enums.Status;
import com.company.exam.identity.entity.Organization;
import com.company.exam.identity.entity.User;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * D-11: the audit trail is scoped by organization (ADR-0046, Gate B answer B4).
 *
 * <p>Before the fix, the ADMIN explorer returned all 5,867 rows of a two-organization deployment.
 * Organization was derived through user at read time, which reported nothing for entities with no
 * user (organizations, business rules, providers). The write path now records the organization and
 * the read path filters on it. A NULL organization means platform-wide activity and is shown to no
 * tenant.
 *
 * <p><b>How the fixture works.</b> The base class creates users directly through the repository,
 * which emits no audit row. So these tests drive the real SecurityEventLogger to produce
 * attributable rows — otherwise the assertions would pass against a table this fixture never wrote
 * to, which is the failure mode the first draft of this class actually had.
 */
class AuditLogOrganizationScopeIntegrationTest extends AbstractIdentityIntegrationTest {

    private static final String PASSWORD = "Correct-Horse-Battery-9";

    @Autowired
    private SecurityEventLogger securityEvents;

    private Organization newOrganization() {
        return createOrganization("AUD_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8),
                Status.ACTIVE);
    }

    /** Creates an ADMIN holding AUDIT_LOG_VIEW, which ADMIN does not get by default. */
    private String auditorOf(UUID organizationId) {
        User user = createUserInOrganization(organizationId, syntheticEmail("auditor"), PASSWORD,
                Status.ACTIVE, "ADMIN");
        grantPermission(user.getId(), "AUDIT_LOG_VIEW");
        return bearer(tokenFor(user));
    }

    private void grantPermission(UUID userId, String code) {
        com.company.exam.identity.entity.Role role = new com.company.exam.identity.entity.Role();
        role.setCode("AUDITOR_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase());
        role.setName("Auditor");
        role.setStatus(Status.ACTIVE);
        roleRepository.save(role);

        com.company.exam.identity.entity.RolePermission grant =
                new com.company.exam.identity.entity.RolePermission();
        grant.setRoleId(role.getId());
        grant.setPermissionId(permissionRepository.findByCode(code).orElseThrow().getId());
        grant.setStatus(Status.ACTIVE);
        rolePermissionRepository.save(grant);

        com.company.exam.identity.entity.UserRole userRole = new com.company.exam.identity.entity.UserRole();
        userRole.setUserId(userId);
        userRole.setRoleId(role.getId());
        userRole.setStatus(Status.ACTIVE);
        userRoleRepository.save(userRole);
    }

    @Test
    void theAuditExplorerReturnsOnlyTheCallersOrganization() throws Exception {
        Organization mine = newOrganization();
        Organization other = newOrganization();

        User member = createUserInOrganization(mine.getId(), syntheticEmail("audited-mine"), PASSWORD,
                Status.ACTIVE, "STUDENT");
        User foreign = createUserInOrganization(other.getId(), syntheticEmail("audited-other"), PASSWORD,
                Status.ACTIVE, "STUDENT");

        securityEvents.audit(member.getId(), "UPDATE", "User", member.getId(), "mine");
        securityEvents.audit(foreign.getId(), "UPDATE", "User", foreign.getId(), "other");

        String body = mockMvc.perform(get("/api/v1/audit-logs?size=200")
                        .header(AUTHORIZATION, auditorOf(mine.getId())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        List<String> entityIds = com.jayway.jsonpath.JsonPath.read(body, "$.data[*].entityId");
        assertThat(entityIds).doesNotContain(foreign.getId().toString());
    }

    @Test
    void theAuditWritePathRecordsTheOrganization() {
        Organization organization = newOrganization();
        User actor = createUserInOrganization(organization.getId(), syntheticEmail("writer"), PASSWORD,
                Status.ACTIVE, "ADMIN");

        securityEvents.audit(actor.getId(), "UPDATE", "BusinessRule", UUID.randomUUID(), "written");

        boolean attributed = auditLogRepository.findAll().stream()
                .anyMatch(entry -> organization.getId().equals(entry.getOrganizationId()));
        assertThat(attributed).isTrue();
    }

    @Test
    void anOrganizationScopedEntityIsAttributedToThatOrganizationEvenWithNoActor() {
        // Rule 1 of the resolution order: when the audited entity IS an organization, the row belongs
        // to it. This is the case read-time derivation could never cover, because the entity has no
        // user to derive from.
        Organization organization = newOrganization();

        securityEvents.audit(null, "CREATE", "Organization", organization.getId(), "created");

        boolean attributed = auditLogRepository.findAll().stream()
                .anyMatch(entry -> organization.getId().equals(entry.getOrganizationId()));
        assertThat(attributed).isTrue();
    }

    @Test
    void platformWideRowsAreNotShownToATenant() throws Exception {
        securityEvents.audit(null, "SWEEP", "Session", UUID.randomUUID(), "platform-wide");

        Set<String> unattributedIds = auditLogRepository.findAll().stream()
                .filter(entry -> entry.getOrganizationId() == null)
                .map(entry -> entry.getEntityId())
                .filter(java.util.Objects::nonNull)
                .map(UUID::toString)
                .collect(java.util.stream.Collectors.toSet());
        assertThat(unattributedIds).isNotEmpty();

        Organization organization = newOrganization();
        String body = mockMvc.perform(get("/api/v1/audit-logs?size=200")
                        .header(AUTHORIZATION, auditorOf(organization.getId())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        List<String> entityIds = com.jayway.jsonpath.JsonPath.read(body, "$.data[*].entityId");
        for (String returned : entityIds) {
            assertThat(unattributedIds)
                    .as("platform-wide row %s must not reach a tenant", returned)
                    .doesNotContain(returned);
        }
    }
}
