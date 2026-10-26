package com.company.exam.integration.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.company.exam.identity.bootstrap.SuperAdminBootstrapCli;
import com.company.exam.identity.bootstrap.SuperAdminBootstrapDisabledException;
import com.company.exam.identity.bootstrap.SuperAdminBootstrapService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.bind.annotation.RestController;

/**
 * The environment gate for SUPER_ADMIN bootstrap (ADR-0032 deliverable: fail closed).
 *
 * <p>Enabling the mechanism is a two-part decision: {@code app.super-admin.bootstrap.enabled} must be
 * set, <em>and</em> {@code app.environment} must not be production. With both set and the environment
 * forced to production, bootstrap must throw and create no account.
 */
@TestPropertySource(properties = {
        "app.super-admin.bootstrap.enabled=true",
        "app.environment=production"
})
class SuperAdminBootstrapProductionGateIntegrationTest extends AbstractIdentityIntegrationTest {

    @Autowired
    private SuperAdminBootstrapService bootstrapService;

    @Test
    void refusesToBootstrapWhenTheEnvironmentIsProduction() {
        assertThatThrownBy(() -> bootstrapService.bootstrap(
                syntheticEmail("sa-gate"), "Strong-Pass-123"))
                .isInstanceOf(SuperAdminBootstrapDisabledException.class)
                .hasMessageContaining("production");
    }

    @Test
    void leavesNoAccountBehindWhenItRefuses() {
        String email = syntheticEmail("sa-gate-nowrites");
        assertThatThrownBy(() -> bootstrapService.bootstrap(email, "Strong-Pass-123"))
                .isInstanceOf(SuperAdminBootstrapDisabledException.class);
        assertThat(userRepository.findByEmail(email)).isEmpty();
    }

    @Test
    void theExplicitEnvironmentCheckAlsoRefuses() {
        assertThatThrownBy(() -> bootstrapService.assertEnvironmentAllowsBootstrap())
                .isInstanceOf(SuperAdminBootstrapDisabledException.class);
    }

    @Test
    void reportsTheConfiguredEnvironment() {
        assertThat(bootstrapService.currentEnvironment()).isEqualTo("production");
    }

    /** The mechanism must not add a production HTTP surface (ADR-0032). */
    @Test
    void exposesNoHttpEndpoint() {
        Class<?> serviceType = SuperAdminBootstrapService.class;
        assertThat(serviceType.isAnnotationPresent(RestController.class)).isFalse();
        assertThat(serviceType.isAnnotationPresent(org.springframework.stereotype.Controller.class)).isFalse();
        assertThat(serviceType.getDeclaredMethods()).noneMatch(method ->
                method.isAnnotationPresent(org.springframework.web.bind.annotation.RequestMapping.class)
                        || method.isAnnotationPresent(org.springframework.web.bind.annotation.GetMapping.class)
                        || method.isAnnotationPresent(org.springframework.web.bind.annotation.PostMapping.class)
                        || method.isAnnotationPresent(org.springframework.web.bind.annotation.PutMapping.class)
                        || method.isAnnotationPresent(org.springframework.web.bind.annotation.DeleteMapping.class));

        Class<?> cliType = SuperAdminBootstrapCli.class;
        assertThat(cliType.isAnnotationPresent(RestController.class)).isFalse();
        assertThat(cliType.isAnnotationPresent(Component.class)).isFalse();
    }
}
