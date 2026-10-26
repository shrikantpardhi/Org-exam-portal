package com.company.exam.integration.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.exam.identity.IdentityConstants;
import com.company.exam.identity.bootstrap.SuperAdminBootstrapDtos;
import com.company.exam.identity.bootstrap.SuperAdminBootstrapService;
import com.company.exam.identity.entity.User;
import java.util.UUID;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

/**
 * SUPER_ADMIN installation-owner bootstrap (ADR-0032, ADR-0033, ADR-0034).
 *
 * <p>The owner is created exactly once in the PLATFORM system organization, holds the SUPER_ADMIN
 * role, starts with {@code must_change_password}, stores a real hash (never plaintext), and is
 * attributed to the system actor. Re-running is idempotent.
 */
@TestPropertySource(properties = "app.super-admin.bootstrap.enabled=true")
@TestMethodOrder(OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SuperAdminBootstrapProvisioningIntegrationTest extends AbstractIdentityIntegrationTest {

    private static final String BOOTSTRAP_PASSWORD = "Correct-Horse-Battery-9";

    @Autowired
    private SuperAdminBootstrapService bootstrapService;

    private String ownerEmail;

    @Test
    @Order(1)
    void createsTheInstallationOwnerOnce() {
        ownerEmail = syntheticEmail("sa-bootstrap");
        SuperAdminBootstrapDtos.BootstrapResult result = bootstrapService.bootstrap(ownerEmail, BOOTSTRAP_PASSWORD);

        assertThat(result.outcome()).isEqualTo("CREATED");
        assertThat(result.userId()).isNotNull();

        User owner = userRepository.findByEmail(ownerEmail).orElseThrow();
        assertThat(owner.getOrganizationId())
                .isEqualTo(organizationRepository.findByCode("PLATFORM").orElseThrow().getId());
        assertThat(owner.isMustChangePassword()).isTrue();
        assertThat(owner.getStatus()).isEqualTo(com.company.exam.common.enums.Status.ACTIVE);
        assertThat(roleRepository.findEffectiveRoleCodes(owner.getId())).contains("SUPER_ADMIN");

        // The credential is a real encoder hash, never the plaintext password (ADR-0032).
        assertThat(owner.getPassword()).doesNotContain(BOOTSTRAP_PASSWORD);
        assertThat(owner.getPassword()).startsWith("{");
        assertThat(passwordEncoder.matches(BOOTSTRAP_PASSWORD, owner.getPassword())).isTrue();
    }

    @Test
    @Order(2)
    void rerunningIsIdempotentAndLeavesTheOwnerUntouched() {
        UUID platformOrgId = organizationRepository.findByCode("PLATFORM").orElseThrow().getId();

        // A second run, even with a different email, must not create a second owner.
        SuperAdminBootstrapDtos.BootstrapResult again = bootstrapService.bootstrap(
                syntheticEmail("sa-bootstrap-again"), "Another-Strong-Pass-1");

        assertThat(again.outcome()).isEqualTo("ALREADY_EXISTS");
        assertThat(again.userId()).isNull();
        assertThat(userRoleRepository.countActiveAssignmentsByRoleCodeInOrganization("SUPER_ADMIN", platformOrgId))
                .isEqualTo(1);
    }

    @Test
    @Order(3)
    void attributesTheOwnerToTheSystemActor() {
        User owner = userRepository.findByEmail(ownerEmail).orElseThrow();
        assertThat(owner.getCreatedBy()).isEqualTo(IdentityConstants.SYSTEM_ACTOR_ID);
        assertThat(owner.getUpdatedBy()).isEqualTo(IdentityConstants.SYSTEM_ACTOR_ID);
        assertThat(owner.getCreatedOn()).isNotNull();
    }
}
