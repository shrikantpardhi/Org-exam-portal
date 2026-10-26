package com.company.exam.integration.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.exam.identity.bootstrap.SuperAdminBootstrapProperties;
import com.company.exam.identity.bootstrap.SuperAdminBootstrapService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.MergedAnnotation;
import org.springframework.core.annotation.MergedAnnotations;

/**
 * The off-by-default half of the SUPER_ADMIN bootstrap gate (ADR-0032).
 *
 * <p>Asserts the declared {@code @ConditionalOnProperty} contract rather than booting a second full
 * context: with {@code app.super-admin.bootstrap.enabled} absent, the bean is not created, so there
 * is no code path to it in a deployment that never opted in.
 */
class SuperAdminBootstrapDefaultDisabledTest {

    @Test
    void bootstrapIsOffUnlessExplicitlyEnabled() {
        MergedAnnotations annotations = MergedAnnotations.from(
                SuperAdminBootstrapService.class, MergedAnnotations.SearchStrategy.TYPE_HIERARCHY);
        MergedAnnotation<ConditionalOnProperty> condition = annotations.get(ConditionalOnProperty.class);

        assertThat(condition.isPresent()).isTrue();
        assertThat(condition.getStringArray("name")).containsExactly("app.super-admin.bootstrap.enabled");
        assertThat(condition.getBoolean("matchIfMissing")).isFalse();
        assertThat(condition.getString("havingValue")).isEqualTo("true");
    }

    @Test
    void propertiesDefaultToDisabledAndForbidProduction() {
        SuperAdminBootstrapProperties properties = new SuperAdminBootstrapProperties();
        assertThat(properties.isEnabled()).isFalse();
        assertThat(properties.getForbiddenEnvironment()).isEqualTo("production");
    }
}
