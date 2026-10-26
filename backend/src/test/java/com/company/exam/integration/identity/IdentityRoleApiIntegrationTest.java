package com.company.exam.integration.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.exam.common.enums.Status;
import com.company.exam.identity.entity.Permission;
import com.company.exam.identity.entity.Role;
import com.company.exam.identity.entity.RolePermission;
import com.company.exam.identity.entity.User;
import com.company.exam.identity.entity.UserRole;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * Role management API and the privileged role change policy
 * ({@code specs/10-P1-Identity-V1.5.md §60-§63, §176}; task P1-15).
 */
class IdentityRoleApiIntegrationTest extends AbstractIdentityIntegrationTest {

    private static final String PASSWORD = "Correct-Horse-Battery-9";
    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;

    private User anAdmin() {
        return createUser(syntheticEmail("role-admin"), PASSWORD, "ADMIN");
    }

    @Test
    void adminListsRolesIncludingAdmin() throws Exception {
        String token = tokenFor(anAdmin());

        mockMvc.perform(get("/api/v1/roles").header(AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].code",
                        org.hamcrest.Matchers.hasItem("ADMIN")));
    }

    @Test
    void createRoleSucceedsAndDuplicateCodeConflicts() throws Exception {
        String token = tokenFor(anAdmin());
        String code = "ROLE_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();

        mockMvc.perform(post("/api/v1/roles").header(AUTHORIZATION, bearer(token)).contentType(JSON)
                        .content("{\"code\":\"" + code + "\",\"name\":\"New Role\",\"description\":\"desc\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.code").value(code));

        mockMvc.perform(post("/api/v1/roles").header(AUTHORIZATION, bearer(token)).contentType(JSON)
                        .content("{\"code\":\"" + code + "\",\"name\":\"New Role\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void adminRolePermissionsIncludeUserView() throws Exception {
        String token = tokenFor(anAdmin());

        mockMvc.perform(get("/api/v1/roles/" + role("ADMIN").getId() + "/permissions")
                        .header(AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].code", org.hamcrest.Matchers.hasItem("USER_VIEW")));
    }

    @Test
    void rolePermissionsAreReplacedTransactionally() throws Exception {
        String token = tokenFor(anAdmin());
        Role role = new Role();
        role.setCode("REPLACE_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase());
        role.setName("Replace role");
        role.setStatus(Status.ACTIVE);
        roleRepository.save(role);

        mockMvc.perform(put("/api/v1/roles/" + role.getId() + "/permissions")
                        .header(AUTHORIZATION, bearer(token)).contentType(JSON)
                        .content("{\"permissionIds\":[\"" + permission("PERMISSION_VIEW").getId() + "\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].code", org.hamcrest.Matchers.hasItem("PERMISSION_VIEW")));

        mockMvc.perform(put("/api/v1/roles/" + role.getId() + "/permissions")
                        .header(AUTHORIZATION, bearer(token)).contentType(JSON)
                        .content("{\"permissionIds\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void replacingWithAnUnknownPermissionIsRejected() throws Exception {
        String token = tokenFor(anAdmin());

        mockMvc.perform(put("/api/v1/roles/" + role("STUDENT").getId() + "/permissions")
                        .header(AUTHORIZATION, bearer(token)).contentType(JSON)
                        .content("{\"permissionIds\":[\"" + UUID.randomUUID() + "\"]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aUserCannotModifyThePermissionsOfTheirOwnRole() throws Exception {
        // The admin holds the ADMIN role, so editing ADMIN's permissions is self-modification.
        String token = tokenFor(anAdmin());

        mockMvc.perform(put("/api/v1/roles/" + role("ADMIN").getId() + "/permissions")
                        .header(AUTHORIZATION, bearer(token)).contentType(JSON)
                        .content("{\"permissionIds\":[\"" + permission("PERMISSION_VIEW").getId() + "\"]}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errors[0].code").value("SELF_PERMISSION_MODIFICATION"));
    }

    @Test
    void adminRoleAssignmentRequiresPermissionAndEnforcesTheOrgCap() throws Exception {
        // Without the ADMIN_ROLE_ASSIGN permission: denied.
        String plainAdminToken = tokenFor(anAdmin());
        User target = createUser(syntheticEmail("assign-target"), PASSWORD, "STUDENT");
        mockMvc.perform(put("/api/v1/users/" + target.getId()).header(AUTHORIZATION, bearer(plainAdminToken))
                        .contentType(JSON).content(rolesBody("ADMIN")))
                .andExpect(status().isForbidden());

        // With the permission but the DEFAULT org already has an ADMIN: the one-ADMIN-per-org cap
        // (ADR-0032 C3) rejects the grant instead of creating a second administrator.
        String assignerToken = tokenFor(anAdminAssigner());
        mockMvc.perform(put("/api/v1/users/" + target.getId()).header(AUTHORIZATION, bearer(assignerToken))
                        .contentType(JSON).content(rolesBody("ADMIN")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].code").value("ADMIN_CAP_EXCEEDED"));
    }

    @Test
    void removingTheLastActiveAdminIsBlocked() throws Exception {
        String token = tokenFor(anAdminAssigner());
        User onlyAdmin = createUser(syntheticEmail("last-admin"), PASSWORD, "ADMIN");

        // Normalize the ADMIN population so this user is the only active administrator.
        for (UserRole assignment : userRoleRepository.findByRoleIdAndStatus(role("ADMIN").getId(), Status.ACTIVE)) {
            if (!assignment.getUserId().equals(onlyAdmin.getId())) {
                assignment.setStatus(Status.INACTIVE);
                userRoleRepository.save(assignment);
            }
        }
        assertThat(userRoleRepository.countActiveAssignmentsByRoleCode("ADMIN")).isEqualTo(1);

        mockMvc.perform(put("/api/v1/users/" + onlyAdmin.getId()).header(AUTHORIZATION, bearer(token))
                        .contentType(JSON).content("{\"roles\":[]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].code").value("LAST_ADMIN"));
    }

    private String rolesBody(String roleCode) {
        return "{\"roles\":[\"" + role(roleCode).getId() + "\"]}";
    }

    /** Creates a user whose test role holds ADMIN_ROLE_ASSIGN, plus a role to edit. */
    private User anAdminAssigner() {
        Permission adminRoleAssign = permission("ADMIN_ROLE_ASSIGN");
        Role assignerRole = new Role();
        assignerRole.setCode("ASSIGNER_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase());
        assignerRole.setName("Assigner");
        assignerRole.setStatus(Status.ACTIVE);
        roleRepository.save(assignerRole);

        RolePermission rolePermission = new RolePermission();
        rolePermission.setRoleId(assignerRole.getId());
        rolePermission.setPermissionId(adminRoleAssign.getId());
        rolePermission.setStatus(Status.ACTIVE);
        rolePermissionRepository.save(rolePermission);

        // The assigner also needs USER_UPDATE to reach the role policy.
        Permission userUpdate = permission("USER_UPDATE");
        RolePermission userUpdateGrant = new RolePermission();
        userUpdateGrant.setRoleId(assignerRole.getId());
        userUpdateGrant.setPermissionId(userUpdate.getId());
        userUpdateGrant.setStatus(Status.ACTIVE);
        rolePermissionRepository.save(userUpdateGrant);

        User user = createUser(syntheticEmail("assigner"), PASSWORD);
        UserRole userRole = new UserRole();
        userRole.setUserId(user.getId());
        userRole.setRoleId(assignerRole.getId());
        userRole.setStatus(Status.ACTIVE);
        userRoleRepository.save(userRole);
        return user;
    }
}
