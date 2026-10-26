package com.company.exam.integration.identity;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.company.exam.common.enums.Status;
import com.company.exam.identity.entity.Organization;
import com.company.exam.identity.entity.Permission;
import com.company.exam.identity.entity.Role;
import com.company.exam.identity.entity.User;
import com.company.exam.identity.entity.UserRole;
import com.company.exam.identity.repository.OrganizationRepository;
import com.company.exam.identity.repository.PermissionRepository;
import com.company.exam.identity.repository.RoleRepository;
import com.company.exam.identity.repository.UserRepository;
import com.company.exam.identity.repository.UserRoleRepository;
import com.company.exam.identity.service.SessionService;
import com.company.exam.identity.security.JwtTokenIssuer;
import com.company.exam.support.AbstractIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Shared setup for Identity integration tests.
 *
 * <p>Creates clearly synthetic accounts and issues tokens directly for authorization tests (which
 * do not exercise the login endpoint), while authentication tests use the real HTTP login.
 */
@AutoConfigureMockMvc
public abstract class AbstractIdentityIntegrationTest extends AbstractIntegrationTest {

    protected static final String AUTHORIZATION = "Authorization";

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected RoleRepository roleRepository;

    @Autowired
    protected PermissionRepository permissionRepository;

    @Autowired
    protected UserRoleRepository userRoleRepository;

    @Autowired
    protected com.company.exam.identity.repository.RolePermissionRepository rolePermissionRepository;

    @Autowired
    protected com.company.exam.identity.service.AuthorizationService authorizationService;

    @Autowired
    protected OrganizationRepository organizationRepository;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    @Autowired
    protected SessionService sessionService;

    @Autowired
    protected JwtTokenIssuer tokenIssuer;

    @Autowired
    protected com.company.exam.administration.repository.AuditLogRepository auditLogRepository;

    protected UUID defaultOrganizationId() {
        return organizationRepository.findByCode("DEFAULT").orElseThrow().getId();
    }

    protected User createUser(String email, String rawPassword, String... roleCodes) {
        return createUserInOrganization(defaultOrganizationId(), email, rawPassword, Status.ACTIVE, roleCodes);
    }

    protected User createUserInOrganization(UUID organizationId, String email, String rawPassword, Status status,
            String... roleCodes) {
        User user = new User();
        user.setOrganizationId(organizationId);
        user.setFirstName("Test");
        user.setLastName("User");
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setStatus(status);
        User saved = userRepository.save(user);
        for (String roleCode : roleCodes) {
            assignRole(saved.getId(), roleCode);
        }
        return saved;
    }

    protected Role role(String code) {
        // The seeded roles are platform-wide after V35 (ADR-0045), so the code-only lookup every test
        // used is now explicitly the platform-wide one.
        return roleRepository.findByCodeAndOrganizationIdIsNull(code)
                .orElseThrow(() -> new IllegalStateException("missing role " + code));
    }

    protected Permission permission(String code) {
        return permissionRepository.findByCode(code)
                .orElseThrow(() -> new IllegalStateException("missing permission " + code));
    }

    protected void assignRole(UUID userId, String roleCode) {
        UserRole userRole = new UserRole();
        userRole.setUserId(userId);
        userRole.setRoleId(role(roleCode).getId());
        userRole.setStatus(Status.ACTIVE);
        userRoleRepository.save(userRole);
    }

    /** Issues a token backed by a real ACTIVE login session. */
    protected String tokenFor(User user) {
        UUID sessionId = sessionService.create(user.getId(), "127.0.0.1", "test-agent").getId();
        return tokenIssuer.issue(user.getId(), sessionId).token();
    }

    protected String bearer(String token) {
        return "Bearer " + token;
    }

    protected String syntheticEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@example.com";
    }

    /** Performs a real login and returns the access token. */
    protected String login(String email, String password) throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.accessToken");
    }

    protected Organization createOrganization(String code, Status status) {
        Organization organization = new Organization();
        organization.setCode(code);
        organization.setName(code);
        organization.setStatus(status);
        return organizationRepository.save(organization);
    }
}
