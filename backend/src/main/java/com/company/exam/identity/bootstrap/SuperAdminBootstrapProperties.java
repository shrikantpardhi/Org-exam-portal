package com.company.exam.identity.bootstrap;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the SUPER_ADMIN bootstrap mechanism (ADR-0032, ADR-0033).
 *
 * <p>The installation owner is created exactly once by an explicit operator action, never at runtime
 * and never through the API. Two independent switches must both allow it: the
 * {@code app.super-admin.bootstrap.enabled} property and a non-production {@code app.environment}. The
 * property defaults to {@code false}, and the environment gate is enforced in
 * {@link SuperAdminBootstrapService} so enabling the property in production still fails closed.
 */
@ConfigurationProperties(prefix = "app.super-admin.bootstrap")
public class SuperAdminBootstrapProperties {

    /** Whether the bootstrap bean exists at all. Defaults to {@code false}. */
    private boolean enabled = false;

    /** The environment value that must never permit bootstrap. Compared case-insensitively. */
    private String forbiddenEnvironment = "production";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getForbiddenEnvironment() {
        return forbiddenEnvironment;
    }

    public void setForbiddenEnvironment(String forbiddenEnvironment) {
        this.forbiddenEnvironment = forbiddenEnvironment;
    }
}
