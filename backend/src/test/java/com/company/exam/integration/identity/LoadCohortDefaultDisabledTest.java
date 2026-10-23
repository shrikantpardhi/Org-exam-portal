package com.company.exam.integration.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.exam.identity.loadcohort.LoadCohortProvisioningService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.MergedAnnotation;
import org.springframework.core.annotation.MergedAnnotations;

/**
 * The off-by-default half of the GAP-085 gate.
 *
 * <p>The environment gate is proved by {@link LoadCohortProductionGateIntegrationTest}. This test proves
 * the other switch: with {@code app.load-cohort.enabled} absent or false, the provisioning bean is not
 * created at all, so there is no code path to it in a deployment that never opted in.
 *
 * <p>It asserts the declared contract rather than booting a full context twice, because the condition is
 * evaluated by Spring at startup and a second full context per test class is expensive. If the annotation's
 * {@code havingValue} or {@code name} were ever loosened, the other integration tests would start seeing the
 * bean without the property and this test would flag the change.
 */
class LoadCohortDefaultDisabledTest {

    @Test
    void provisioningIsOffUnlessExplicitlyEnabled() {
        MergedAnnotations annotations = MergedAnnotations.from(
                LoadCohortProvisioningService.class, MergedAnnotations.SearchStrategy.TYPE_HIERARCHY);
        MergedAnnotation<ConditionalOnProperty> condition =
                annotations.get(ConditionalOnProperty.class);

        assertThat(condition.isPresent()).isTrue();
        // `name` is a String[] on the annotation, so compare the array rather than a single value.
        assertThat(condition.getStringArray("name")).containsExactly("app.load-cohort.enabled");
        // No matchIfMissing: absent property must mean "no bean".
        assertThat(condition.getBoolean("matchIfMissing")).isFalse();
        assertThat(condition.getString("havingValue")).isEqualTo("true");
    }

    @Test
    void propertiesDefaultToDisabled() {
        com.company.exam.identity.loadcohort.LoadCohortProperties properties =
                new com.company.exam.identity.loadcohort.LoadCohortProperties();

        assertThat(properties.isEnabled()).isFalse();
        assertThat(properties.getForbiddenEnvironment()).isEqualTo("production");
        assertThat(properties.getMaxCohortSize()).isPositive();
    }
}
