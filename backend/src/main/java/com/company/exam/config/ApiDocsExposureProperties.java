package com.company.exam.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * API-documentation exposure switch (closes GAP-136, {@code specs/21-P12 §26}).
 *
 * <p>Swagger UI and the OpenAPI document describe the entire API surface, including every
 * parameter, schema, and enumeration. That is a development and integration aid, never a
 * production affordance: publishing it to unauthenticated callers discloses the full attack
 * surface without requiring a single probe.
 *
 * <p>The switch is deliberately advisory rather than authoritative. {@link SecurityExposurePolicy}
 * combines it with the environment and the active profiles, so enabling it here cannot make the
 * docs public in production — exactly the two-gate arrangement used for synthetic load-cohort
 * provisioning (GAP-085), and for the same reason: a single misconfigured property must not
 * become an incident.
 */
@ConfigurationProperties(prefix = "app.security.exposure")
public class ApiDocsExposureProperties {

    /**
     * Whether the API documentation may be reachable without authentication.
     *
     * <p>Defaults to {@code true} so the local developer experience is unchanged; production
     * disables it in {@code application-prod.yml} and {@link SecurityExposurePolicy} refuses to
     * honour it there regardless of this value.
     */
    private boolean apiDocsEnabled = true;

    public boolean isApiDocsEnabled() {
        return apiDocsEnabled;
    }

    public void setApiDocsEnabled(boolean apiDocsEnabled) {
        this.apiDocsEnabled = apiDocsEnabled;
    }
}
