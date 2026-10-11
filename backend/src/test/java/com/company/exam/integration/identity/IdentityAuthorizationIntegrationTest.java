package com.company.exam.integration.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.exam.common.enums.Status;
import com.company.exam.identity.entity.Permission;
import com.company.exam.identity.entity.Role;
import com.company.exam.identity.entity.RolePermission;
import com.company.exam.identity.entity.User;
import com.company.exam.identity.entity.UserRole;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * The Identity authorization matrix ({@code specs/10-P1-Identity-V1.5.md §68, §170};
 * task P1-19).
 *
 * <p>Proves 401 for anonymous callers, 403 for authenticated callers without the permission, 200
 * with it, and that the five-way ACTIVE conjunction is enforced.
 */
class IdentityAuthorizationIntegrationTest extends AbstractIdentityIntegrationTest {

    private static final String PASSWORD = "Correct-Horse-Battery-9";

    @Test
    void unauthenticatedRequestIsRejectedWith401() throws Exception {
        mockMvc.perform(get("/api/v1/users")).andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedWithoutPermissionIsRejectedWith403() throws Exception {
        User student = createUser(syntheticEmail("student-403"), PASSWORD, "STUDENT");
        String token = tokenFor(student);

        mockMvc.perform(get("/api/v1/users").header(AUTHORIZATION, bearer(token)))
                .andExpect(status().isForbidden());
    }

    @Test
    void authenticatedWithPermissionSucceeds() throws Exception {
        User admin = createUser(syntheticEmail("admin-200"), PASSWORD, "ADMIN");
        String token = tokenFor(admin);

        mockMvc.perform(get("/api/v1/users").header(AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk());
    }

    @Test
    void selfServiceEndpointsRequireOnlyAuthentication() throws Exception {
        User student = createUser(syntheticEmail("student-self"), PASSWORD, "STUDENT");
        String token = tokenFor(student);

        mockMvc.perform(get("/api/v1/me").header(AUTHORIZATION, bearer(token))).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/me/permissions").header(AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk());
    }

    @Test
    void inactiveRelationshipsAndStatusesDoNotGrantAPermission() {
        String permissionCode = "MATRIX_VIEW_" + UUID.randomUUID().toString().replace("-", "");
        Permission permission = new Permission();
        permission.setCode(permissionCode);
        permission.setName(permissionCode);
        permission.setStatus(Status.ACTIVE);
        permissionRepository.save(permission);

        Role role = new Role();
        role.setCode("MATRIX_ROLE_" + UUID.randomUUID().toString().replace("-", "").toUpperCase());
        role.setName("Matrix role");
        role.setStatus(Status.ACTIVE);
        roleRepository.save(role);

        RolePermission rolePermission = new RolePermission();
        rolePermission.setRoleId(role.getId());
        rolePermission.setPermissionId(permission.getId());
        rolePermission.setStatus(Status.ACTIVE);
        rolePermissionRepository.save(rolePermission);

        User user = createUser(syntheticEmail("matrix"), PASSWORD);
        UserRole userRole = new UserRole();
        userRole.setUserId(user.getId());
        userRole.setRoleId(role.getId());
        userRole.setStatus(Status.ACTIVE);
        userRoleRepository.save(userRole);

        assertThat(authorizationService.hasPermission(user.getId(), permissionCode)).isTrue();

        userRole.setStatus(Status.INACTIVE);
        userRoleRepository.save(userRole);
        assertThat(authorizationService.hasPermission(user.getId(), permissionCode)).isFalse();

        userRole.setStatus(Status.ACTIVE);
        userRoleRepository.save(userRole);
        rolePermission.setStatus(Status.INACTIVE);
        rolePermissionRepository.save(rolePermission);
        assertThat(authorizationService.hasPermission(user.getId(), permissionCode)).isFalse();

        rolePermission.setStatus(Status.ACTIVE);
        rolePermissionRepository.save(rolePermission);
        permission.setStatus(Status.INACTIVE);
        permissionRepository.save(permission);
        assertThat(authorizationService.hasPermission(user.getId(), permissionCode)).isFalse();

        permission.setStatus(Status.ACTIVE);
        permissionRepository.save(permission);
        role.setStatus(Status.INACTIVE);
        roleRepository.save(role);
        assertThat(authorizationService.hasPermission(user.getId(), permissionCode)).isFalse();

        role.setStatus(Status.ACTIVE);
        roleRepository.save(role);
        user.setStatus(Status.ARCHIVED);
        userRepository.save(user);
        assertThat(authorizationService.hasPermission(user.getId(), permissionCode)).isFalse();
    }
}
