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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * D-04 and D-09: bounded delegation — a caller may only grant permissions it holds
 * ({@code 20-P11-Administration-V1.5.md} §17, ADR-0040).
 *
 * <p>{@code ADMIN_ROLE_ASSIGN} is reserved-but-not-granted ({@code 10-P1} §65) precisely so that an
 * ADMIN cannot hand it out. Before the fix an ADMIN granted it to a disposable role with a single
 * {@code PUT} and received 200 (D-04). The refusal must be {@code PERMISSION_NOT_HELD}, not
 * {@code RESERVED_PERMISSION_DENIED}: the two rules are independent and a test that could not tell
 * them apart would hide a regression in either.
 */
class BoundedDelegationIntegrationTest extends AbstractIdentityIntegrationTest {

    private static final String PASSWORD = "Super-Strong-Pass-1";
    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;

    private Role disposableRole(String prefix) {
        Role role = new Role();
        role.setCode(prefix + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase());
        role.setName(prefix + " role");
        role.setStatus(Status.ACTIVE);
        return roleRepository.save(role);
    }

    /** A user holding exactly the given permission codes, via one disposable role. */
    private String bearerWith(String prefix, String... permissionCodes) {
        Role role = disposableRole(prefix);
        for (String code : permissionCodes) {
            RolePermission grant = new RolePermission();
            grant.setRoleId(role.getId());
            grant.setPermissionId(permission(code).getId());
            grant.setStatus(Status.ACTIVE);
            rolePermissionRepository.save(grant);
        }
        User user = createUser(syntheticEmail(prefix), PASSWORD);
        UserRole userRole = new UserRole();
        userRole.setUserId(user.getId());
        userRole.setRoleId(role.getId());
        userRole.setStatus(Status.ACTIVE);
        userRoleRepository.save(userRole);
        return bearer(tokenFor(user));
    }

    private String body(List<UUID> permissionIds) {
        return "{\"permissionIds\":[" + permissionIds.stream().map(id -> "\"" + id + "\"")
                .collect(java.util.stream.Collectors.joining(",")) + "]}";
    }

    private UUID idOf(String code) {
        return permission(code).getId();
    }

    @Test
    void anAdminCannotGrantAdminRoleAssignBecauseItDoesNotHoldIt() throws Exception {
        // The exact call that returned 200 before the fix (D-04).
        String token = bearerWith("delegate-admin", "ROLE_PERMISSION_ASSIGN", "ROLE_VIEW");
        UUID target = disposableRole("D04TARGET").getId();

        mockMvc.perform(put("/api/v1/roles/" + target + "/permissions")
                        .header(AUTHORIZATION, token).contentType(JSON)
                        .content(body(List.of(idOf("ADMIN_ROLE_ASSIGN")))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errors[0].code").value("PERMISSION_NOT_HELD"));

        org.assertj.core.api.Assertions.assertThat(permissionRepository.findEffectiveByRoleId(target)).isEmpty();
    }

    @Test
    void theDelegationRefusalIsDistinctFromTheReservedRefusal() throws Exception {
        // PLATFORM_* is refused by the reserved rule (RESERVED_PERMISSION_DENIED); an ordinary
        // unheld code is refused by delegation (PERMISSION_NOT_HELD). Asserting both codes in one
        // test pins the precedence and the distinction in the same place.
        String token = bearerWith("delegate-both", "ROLE_PERMISSION_ASSIGN", "ROLE_VIEW");
        UUID target = disposableRole("BOTH").getId();

        mockMvc.perform(put("/api/v1/roles/" + target + "/permissions")
                        .header(AUTHORIZATION, token).contentType(JSON)
                        .content(body(List.of(idOf("PLATFORM_STATUS_VIEW")))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errors[0].code").value("RESERVED_PERMISSION_DENIED"));

        mockMvc.perform(put("/api/v1/roles/" + target + "/permissions")
                        .header(AUTHORIZATION, token).contentType(JSON)
                        .content(body(List.of(idOf("USER_ARCHIVE")))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errors[0].code").value("PERMISSION_NOT_HELD"));
    }

    @Test
    void aCallerMayGrantWhatItHolds() throws Exception {
        // The rule must not break ordinary administration: an ADMIN grants the permissions it has.
        String token = bearerWith("delegate-allowed", "ROLE_PERMISSION_ASSIGN", "ROLE_VIEW", "USER_VIEW");
        UUID target = disposableRole("ALLOWED").getId();

        mockMvc.perform(put("/api/v1/roles/" + target + "/permissions")
                        .header(AUTHORIZATION, token).contentType(JSON)
                        .content(body(List.of(idOf("ROLE_VIEW"), idOf("USER_VIEW")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].code", org.hamcrest.Matchers.hasItem("USER_VIEW")));
    }

    @Test
    void revokingIsAlwaysAllowedEvenForCodesTheCallerDoesNotHold() throws Exception {
        // Bounded delegation constrains what a caller may *grant*. A caller that ends up holding an
        // escalated code by some earlier accident must still be able to remove it.
        String token = bearerWith("delegate-revoke", "ROLE_PERMISSION_ASSIGN", "ROLE_VIEW");
        UUID target = disposableRole("REVOKE").getId();
        String narrow = body(List.of(idOf("ROLE_VIEW")));

        mockMvc.perform(put("/api/v1/roles/" + target + "/permissions")
                        .header(AUTHORIZATION, token).contentType(JSON).content(narrow))
                .andExpect(status().isOk());

        // Empty set: revoking everything is never an escalation.
        mockMvc.perform(put("/api/v1/roles/" + target + "/permissions")
                        .header(AUTHORIZATION, token).contentType(JSON)
                        .content("{\"permissionIds\":[]}"))
                .andExpect(status().isOk());
        org.assertj.core.api.Assertions.assertThat(permissionRepository.findEffectiveByRoleId(target)).isEmpty();
    }

    @Test
    void theDelegationRuleIsNotDefeatedByAnAbbreviatedRequest() throws Exception {
        // Granting one unheld code alongside many held ones must still refuse: the check is on the
        // whole desired set, not on a first element or a sample.
        String token = bearerWith("delegate-sneak", "ROLE_PERMISSION_ASSIGN", "ROLE_VIEW");
        UUID target = disposableRole("SNEAK").getId();
        List<UUID> mixed = new ArrayList<>(List.of(idOf("ROLE_VIEW")));
        mixed.add(idOf("AUDIT_LOG_VIEW"));

        mockMvc.perform(put("/api/v1/roles/" + target + "/permissions")
                        .header(AUTHORIZATION, token).contentType(JSON).content(body(mixed)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errors[0].code").value("PERMISSION_NOT_HELD"));
    }
}
