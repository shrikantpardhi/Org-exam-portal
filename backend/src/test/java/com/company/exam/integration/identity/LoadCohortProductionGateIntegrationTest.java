package com.company.exam.integration.identity;

import static org.assertj.core.api.Assertions.assertThat;

import static org.assertj.core.api.Assertions.assertThatThrownBy;


import com.company.exam.identity.loadcohort.LoadCohortDisabledException;
import com.company.exam.identity.loadcohort.LoadCohortDtos;
import com.company.exam.identity.loadcohort.LoadCohortProvisioningService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

/**
 * The environment gate for synthetic load-cohort provisioning (GAP-085 deliverable 3, first criterion).
 *
 * <p>The gate must fail closed. Enabling the mechanism is a two-part decision: an operator must set
 * {@code app.load-cohort.enabled}, <em>and</em> {@code app.environment} must not be production. This test
 * sets the enabling property and then overwrites {@code app.environment} with {@code production}, which is
 * exactly the situation a misconfigured production deployment would produce. Provisioning must throw, and
 * must leave no account behind.
 */
@TestPropertySource(properties = {
        "app.load-cohort.enabled=true",
        "app.environment=production"
})
class LoadCohortProductionGateIntegrationTest extends AbstractIdentityIntegrationTest {

    @Autowired
    private LoadCohortProvisioningService provisioningService;

    @Test
    void refusesToProvisionWhenTheEnvironmentIsProduction() {
        List<LoadCohortDtos.CohortMember> members = List.of(new LoadCohortDtos.CohortMember(
                "gate-" + java.util.UUID.randomUUID() + "@example.test", "Load", "Student"));

        assertThatThrownBy(() -> provisioningService.provision(members))
                .isInstanceOf(LoadCohortDisabledException.class)
                .hasMessageContaining("production");
    }

    /** The refusal must be a refusal, not a partial write: no account may be created. */
    @Test
    void leavesNoAccountBehindWhenItRefuses() {
        String email = "gate-noWrites-" + java.util.UUID.randomUUID() + "@example.test";
        List<LoadCohortDtos.CohortMember> members =
                List.of(new LoadCohortDtos.CohortMember(email, "Load", "Student"));

        assertThatThrownBy(() -> provisioningService.provision(members))
                .isInstanceOf(LoadCohortDisabledException.class);

        assertThat(userRepository.findByEmail(email)).isEmpty();
    }

    /** Even the explicit precondition check refuses, so a caller cannot bypass it by calling it first. */
    @Test
    void theExplicitEnvironmentCheckAlsoRefuses() {
        assertThatThrownBy(() -> provisioningService.assertEnvironmentAllowsProvisioning())
                .isInstanceOf(LoadCohortDisabledException.class);
    }

    @Test
    void reportsTheConfiguredEnvironment() {
        assertThat(provisioningService.currentEnvironment()).isEqualTo("production");
    }

    /**
     * A widened reference to the gate: the same instance refuses regardless of how many times the check is
     * evaluated, so a caller cannot "consume" an allowance.
     */
    @Test
    void refusesConsistentlyAcrossRepeatedAttempts() {
        List<LoadCohortDtos.CohortMember> members = List.of(new LoadCohortDtos.CohortMember(
                "gate-repeat-" + java.util.UUID.randomUUID() + "@example.test", "Load", "Student"));

        for (int attempt = 0; attempt < 3; attempt++) {
            assertThatThrownBy(() -> provisioningService.provision(members))
                    .isInstanceOf(LoadCohortDisabledException.class);
        }
        assertThat(userRepository.findByEmail(members.get(0).email())).isEmpty();
    }

    /** The gate names the offending environment in its message, so an operator can see why. */
    @Test
    void theRefusalMessageIdentifiesTheEnvironment() {
        assertThatThrownBy(() -> provisioningService.assertEnvironmentAllowsProvisioning())
                .hasMessageContaining("production")
                .hasMessageContaining("known credentials");
    }

    /**
     * The mechanism must not add a production HTTP surface.
     *
     * <p>This asserts the constraint directly rather than trusting review: the service is not a
     * controller, carries no request mapping, and the CLI entry point is not a component.
     */
    @Test
    void exposesNoHttpEndpoint() {
        Class<?> serviceType = LoadCohortProvisioningService.class;
        assertThat(serviceType.isAnnotationPresent(
                org.springframework.web.bind.annotation.RestController.class)).isFalse();
        assertThat(serviceType.isAnnotationPresent(
                org.springframework.stereotype.Controller.class)).isFalse();
        assertThat(serviceType.getDeclaredMethods()).noneMatch(method ->
                method.isAnnotationPresent(org.springframework.web.bind.annotation.RequestMapping.class)
                        || method.isAnnotationPresent(org.springframework.web.bind.annotation.GetMapping.class)
                        || method.isAnnotationPresent(org.springframework.web.bind.annotation.PostMapping.class)
                        || method.isAnnotationPresent(org.springframework.web.bind.annotation.PutMapping.class)
                        || method.isAnnotationPresent(
                                org.springframework.web.bind.annotation.DeleteMapping.class));

        Class<?> cliType = com.company.exam.identity.loadcohort.LoadCohortCli.class;
        assertThat(cliType.isAnnotationPresent(
                org.springframework.web.bind.annotation.RestController.class)).isFalse();
        assertThat(cliType.isAnnotationPresent(
                org.springframework.stereotype.Component.class)).isFalse();
    }
}
