package com.company.exam.integration.identity;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.exam.common.enums.Status;
import com.company.exam.identity.entity.Organization;
import com.company.exam.identity.entity.Permission;
import com.company.exam.identity.entity.Role;
import com.company.exam.identity.entity.RolePermission;
import com.company.exam.identity.entity.User;
import com.company.exam.identity.entity.UserRole;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * One-ADMIN-per-organization cap (ADR-0032 C3): a role change that would create a second active
 * ADMIN in an organization is rejected with {@code ADMIN_CAP_EXCEEDED}, while the first ADMIN in
 * an otherwise-empty organization is still assignable.
 */
class AdminCapIntegrationTest extends AbstractIdentityIntegrationTest {

    private static final String PASSWORD = "Correct-Horse-Battery-9";
    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;

    @Test
    void grantingAdminWhenTheOrgAlreadyHasAnAdminIsRejected() throws Exception {
        // DEFAULT org already holds an active ADMIN.
        createUser(syntheticEmail("existing-admin"), PASSWORD, "ADMIN");
        User target = createUser(syntheticEmail("target"), PASSWORD, "STUDENT");
        String assignerToken = tokenFor(anAdminAssigner());

        mockMvc.perform(put("/api/v1/users/" + target.getId())
                        .header(AUTHORIZATION, bearer(assignerToken))
                        .contentType(JSON).content(rolesBody("ADMIN")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].code").value("ADMIN_CAP_EXCEEDED"));
    }

    @Test
    void grantingAdminWhenTheOrgHasNoAdminSucceeds() throws Exception {
        Organization emptyOrg = createOrganization(
                "CAP_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8), Status.ACTIVE);
        User target = createUserInOrganization(emptyOrg.getId(), syntheticEmail("target"), PASSWORD, Status.ACTIVE,
                "STUDENT");
        String assignerToken = tokenFor(anAdminAssigner());

        mockMvc.perform(put("/api/v1/users/" + target.getId())
                        .header(AUTHORIZATION, bearer(assignerToken))
                        .contentType(JSON).content(rolesBody("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roles", Matchers.hasItem("ADMIN")));
    }

    private String rolesBody(String roleCode) {
        return "{\"roles\":[\"" + role(roleCode).getId() + "\"]}";
    }

    /** Creates a user whose test role holds ADMIN_ROLE_ASSIGN plus USER_UPDATE (P1 §63). */
    private User anAdminAssigner() {
        Permission adminRoleAssign = permission("ADMIN_ROLE_ASSIGN");
        Role assignerRole = new Role();
        assignerRole.setCode("ASSIGNER_"
                + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase());
        assignerRole.setName("Assigner");
        assignerRole.setStatus(Status.ACTIVE);
        roleRepository.save(assignerRole);

        RolePermission grant = new RolePermission();
        grant.setRoleId(assignerRole.getId());
        grant.setPermissionId(adminRoleAssign.getId());
        grant.setStatus(Status.ACTIVE);
        rolePermissionRepository.save(grant);

        RolePermission update = new RolePermission();
        update.setRoleId(assignerRole.getId());
        update.setPermissionId(permission("USER_UPDATE").getId());
        update.setStatus(Status.ACTIVE);
        rolePermissionRepository.save(update);

        User user = createUser(syntheticEmail("assigner"), PASSWORD);
        UserRole userRole = new UserRole();
        userRole.setUserId(user.getId());
        userRole.setRoleId(assignerRole.getId());
        userRole.setStatus(Status.ACTIVE);
        userRoleRepository.save(userRole);
        return user;
    }
}
