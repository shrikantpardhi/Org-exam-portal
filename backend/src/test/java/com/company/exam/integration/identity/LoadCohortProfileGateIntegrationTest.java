package com.company.exam.integration.identity;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.company.exam.identity.loadcohort.LoadCohortDisabledException;
import com.company.exam.identity.loadcohort.LoadCohortDtos;
import com.company.exam.identity.loadcohort.LoadCohortProvisioningService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * The production gate must hold even when {@code app.environment} disagrees with the active profile.
 *
 * <p><b>Why this test exists.</b> The first implementation gated only on {@code app.environment}, and an
 * end-to-end run in the real container exposed the hole: {@code docker-compose.yml} sets an
 * {@code APP_ENVIRONMENT} environment variable, which outranks the value in
 * {@code application-prod.yml}, so starting the process with {@code --spring.profiles.active=prod} left
 * {@code app.environment=staging} while the process was genuinely running production. Provisioning
 * <em>succeeded</em> in that state. The gate now checks the active profiles too.
 *
 * <p>This test reproduces exactly that combination: production profile active, {@code app.environment}
 * still reporting a non-production value.
 */
@ActiveProfiles(profiles = {"test", "prod"})
@TestPropertySource(properties = {
        "app.load-cohort.enabled=true",
        // Deliberately the stale value the container's environment variable supplies.
        "app.environment=staging"
})
class LoadCohortProfileGateIntegrationTest extends AbstractIdentityIntegrationTest {

    @Autowired
    private LoadCohortProvisioningService provisioningService;

    @Test
    void refusesWhenTheProductionProfileIsActiveEvenIfAppEnvironmentSaysOtherwise() {
        // The premise of the bug: the declared environment does not say production.
        org.assertj.core.api.Assertions.assertThat(provisioningService.currentEnvironment())
                .isEqualTo("staging");

        List<LoadCohortDtos.CohortMember> members = List.of(new LoadCohortDtos.CohortMember(
                "profilegate-" + java.util.UUID.randomUUID() + "@example.test", "Load", "Student"));

        assertThatThrownBy(() -> provisioningService.provision(members))
                .isInstanceOf(LoadCohortDisabledException.class)
                .hasMessageContaining("prod");
    }

    @Test
    void leavesNoAccountBehindUnderTheProductionProfile() {
        String email = "profilegate-nowrite-" + java.util.UUID.randomUUID() + "@example.test";

        assertThatThrownBy(() -> provisioningService.provision(
                List.of(new LoadCohortDtos.CohortMember(email, "Load", "Student"))))
                .isInstanceOf(LoadCohortDisabledException.class);

        org.assertj.core.api.Assertions.assertThat(userRepository.findByEmail(email)).isEmpty();
    }
}
