package com.company.exam.integration.identity;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.exam.common.enums.Status;
import com.company.exam.identity.entity.Permission;
import com.company.exam.identity.entity.Role;
import com.company.exam.identity.entity.RolePermission;
import com.company.exam.identity.entity.User;
import com.company.exam.identity.entity.UserRole;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * D-03: the reserved permission set ({@code 20-P11-Administration-V1.5.md} §17, ADR-0039).
 *
 * <p>Before the fix an ADMIN placed `PLATFORM_*` on STAFF with a single {@code PUT} and received 200.
 * The reserved set is data (the {@code RESERVED_PERMISSIONS} business rule, {@code V33}) and the
 * bypass permission is {@code UNBOUNDED_PERMISSION_ASSIGN}, which {@code V33} deliberately grants to
 * nobody.
 */
class ReservedPermissionIntegrationTest extends AbstractIdentityIntegrationTest {

    private static final String PASSWORD = "Super-Strong-Pass-1";
    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;

    /** A disposable ACTIVE role with no members, so no self-modification rule interferes. */
    private Role disposableRole(String prefix) {
        Role role = new Role();
        role.setCode(prefix + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase());
        role.setName(prefix + " role");
        role.setStatus(Status.ACTIVE);
        return roleRepository.save(role);
    }

    @org.springframework.beans.factory.annotation.Autowired
    private com.company.exam.administration.api.BusinessRuleResolver ruleResolver;

    private String adminBearer() {
        return bearer(tokenFor(createUser(syntheticEmail("reserved-admin"), PASSWORD, "ADMIN")));
    }

    private String body(List<UUID> permissionIds) {
        return "{\"permissionIds\":[" + permissionIds.stream().map(id -> "\"" + id + "\"")
                .collect(java.util.stream.Collectors.joining(",")) + "]}";
    }

    private UUID idOf(String permissionCode) {
        return permission(permissionCode).getId();
    }

    @Test
    void anAdminCannotPlaceAPlatformPermissionOnARole() throws Exception {
        // The exact call that returned 200 before the fix (D-03).
        UUID roleId = disposableRole("RESERVED").getId();

        mockMvc.perform(put("/api/v1/roles/" + roleId + "/permissions")
                        .header(AUTHORIZATION, adminBearer()).contentType(JSON)
                        .content(body(List.of(idOf("PLATFORM_ORGANIZATION_VIEW")))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errors[0].code").value("RESERVED_PERMISSION_DENIED"));

        org.assertj.core.api.Assertions.assertThat(rolePermissionRepository.findByRoleId(roleId)).isEmpty();
    }

    @Test
    void aReservedRefusalLeavesTheExistingSetUntouched() throws Exception {
        // A mixed request (one allowed code + one reserved) must not partially apply: the operation is
        // transactional and the refusal happens before any row is written.
        UUID roleId = disposableRole("MIXED").getId();
        List<UUID> baseline = List.of(idOf("PERMISSION_VIEW"), idOf("ROLE_VIEW"));

        mockMvc.perform(put("/api/v1/roles/" + roleId + "/permissions")
                        .header(AUTHORIZATION, adminBearer()).contentType(JSON).content(body(baseline)))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/roles/" + roleId + "/permissions")
                        .header(AUTHORIZATION, adminBearer()).contentType(JSON)
                        .content(body(List.of(idOf("PERMISSION_VIEW"), idOf("PLATFORM_STATUS_VIEW")))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errors[0].code").value("RESERVED_PERMISSION_DENIED"));

        org.assertj.core.api.Assertions.assertThat(permissionRepository.findEffectiveByRoleId(roleId))
                .hasSize(2);
    }

    @Test
    void theBypassPermissionIsGrantedToNoSeededRole() throws Exception {
        // 10-P1 §65 precedent: reserved-but-not-granted. The capability exists, is named and is
        // testable, but V33 attaches it to no seeded role, so no seeded principal can use it.
        //
        // The assertion is scoped to the seeded catalogue on purpose: this suite shares one database,
        // and another test in this class legitimately grants the bypass to a disposable role. Pinning
        // "no role anywhere holds it" would make the test order-dependent.
        java.util.Map<UUID, String> seededRoleIds = roleRepository
                .findAllByCodeIn(List.of("ADMIN", "REVIEWER", "STAFF", "STUDENT", "SUPPORT", "FINANCE",
                        "SUPER_ADMIN"))
                .stream().collect(java.util.stream.Collectors.toMap(Role::getId, Role::getCode));

        List<RolePermission> grants = rolePermissionRepository.findAll().stream()
                .filter(grant -> grant.getPermissionId().equals(idOf("UNBOUNDED_PERMISSION_ASSIGN")))
                .filter(grant -> grant.getStatus() == Status.ACTIVE)
                .filter(grant -> seededRoleIds.containsKey(grant.getRoleId()))
                // V34 / Gate A answer A2b grants the bypass to SUPER_ADMIN, so "nobody holds it" is
                // no longer true. What still must hold — and is what D-03 actually needs — is that no
                // *organization-scope* role holds it. A business ADMIN with the bypass would make the
                // reserved set meaningless.
                .filter(grant -> !"SUPER_ADMIN".equals(seededRoleIds.get(grant.getRoleId())))
                .toList();
        org.assertj.core.api.Assertions.assertThat(grants).isEmpty();
    }

    @Test
    void aHolderOfTheBypassPermissionMayPlaceAReservedCode() throws Exception {
        // The rule is a guard, not a blanket ban: it must be satisfiable by the declared bypass, or a
        // legitimate platform-catalogue change would be impossible through the API.
        Role operatorRole = disposableRole("BYPASS");
        Permission bypass = permission("UNBOUNDED_PERMISSION_ASSIGN");
        RolePermission grant = new RolePermission();
        grant.setRoleId(operatorRole.getId());
        grant.setPermissionId(bypass.getId());
        grant.setStatus(Status.ACTIVE);
        rolePermissionRepository.save(grant);
        // The operator also needs ROLE_PERMISSION_ASSIGN to reach the endpoint at all.
        RolePermission assign = new RolePermission();
        assign.setRoleId(operatorRole.getId());
        assign.setPermissionId(idOf("ROLE_PERMISSION_ASSIGN"));
        assign.setStatus(Status.ACTIVE);
        rolePermissionRepository.save(assign);

        User operator = createUser(syntheticEmail("bypass-operator"), PASSWORD);
        UserRole userRole = new UserRole();
        userRole.setUserId(operator.getId());
        userRole.setRoleId(operatorRole.getId());
        userRole.setStatus(Status.ACTIVE);
        userRoleRepository.save(userRole);

        UUID targetRoleId = disposableRole("TARGET").getId();
        mockMvc.perform(put("/api/v1/roles/" + targetRoleId + "/permissions")
                        .header(AUTHORIZATION, bearer(tokenFor(operator))).contentType(JSON)
                        .content(body(List.of(idOf("PLATFORM_STATUS_VIEW")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].code", org.hamcrest.Matchers.hasItem("PLATFORM_STATUS_VIEW")));
    }

    @Test
    void theReservedRuleIsDeclaredAsData() throws Exception {
        // ADR-0023 decision 6: the reserved set must be a business rule, not a Java constant, so a
        // future prefix is covered without a code change.
        var rule = ruleResolver.resolve("RESERVED_PERMISSIONS");
        org.assertj.core.api.Assertions.assertThat(rule).isPresent();
        org.assertj.core.api.Assertions.assertThat(rule.get().values().get("prefixes"))
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST)
                .contains("PLATFORM_");
        org.assertj.core.api.Assertions.assertThat(rule.get().string("bypassPermission"))
                .contains("UNBOUNDED_PERMISSION_ASSIGN");
    }
}
