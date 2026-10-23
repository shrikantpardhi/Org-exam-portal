package com.company.exam.integration.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.exam.identity.entity.User;
import com.company.exam.integration.identity.AbstractIdentityIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/**
 * GAP-138: actuator metrics require authorization outside the monitoring scope.
 *
 * <p>The token is configured for this class so the monitoring path can be exercised end to end; the
 * companion test {@link ActuatorWithoutMonitoringTokenIntegrationTest} covers the unconfigured
 * deployment, which must fail closed rather than fall open.
 *
 * <p>The distinction under test is authorization, not authentication. A caller with a perfectly
 * valid login session is still denied — being signed in is not a licence to read the operational
 * internals of the platform. That is the whole point of the gap, and a test that only checked
 * "anonymous is rejected" would miss it.
 */
@TestPropertySource(properties = "app.security.monitoring.metrics-token=test-monitoring-token")
class ActuatorAuthorizationIntegrationTest extends AbstractIdentityIntegrationTest {

    private static final String MONITORING_TOKEN = "test-monitoring-token";

    @Test
    void anonymousCallerCannotReadMetrics() throws Exception {
        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anonymousCallerCannotReadTheMetricTree() throws Exception {
        mockMvc.perform(get("/actuator/metrics"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedCallerWithoutThePermissionCannotReadMetrics() throws Exception {
        User ordinary = createUser(syntheticEmail("ordinary"), "Password123!", "STUDENT");

        mockMvc.perform(get("/actuator/prometheus").header(AUTHORIZATION, bearer(tokenFor(ordinary))))
                .andExpect(status().isForbidden());
    }

    @Test
    void monitoringTokenCanReadMetrics() throws Exception {
        // The scrape job's credential: metrics readable, nothing else implied by it.
        mockMvc.perform(get("/actuator/prometheus").header(AUTHORIZATION, bearer(MONITORING_TOKEN)))
                .andExpect(status().isOk());
    }

    @Test
    void monitoringTokenCannotActAsAUser() throws Exception {
        // A machine credential scoped to the actuator surface must not authenticate on the business
        // API. If it could, the fix for GAP-138 would have opened a far worse hole than it closed.
        mockMvc.perform(get("/api/v1/system/info").header(AUTHORIZATION, bearer(MONITORING_TOKEN)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void holderOfTheHealthPermissionCanReadMetrics() throws Exception {
        // An operator already uses SYSTEM_HEALTH_VIEW for the administrative health view; requiring
        // a second credential system for the same operational data would be gratuitous.
        User administrator = createUser(syntheticEmail("admin"), "Password123!", "ADMIN");

        mockMvc.perform(get("/actuator/prometheus").header(AUTHORIZATION, bearer(tokenFor(administrator))))
                .andExpect(status().isOk());
    }

    @Test
    void healthStaysPublicForProbes() throws Exception {
        // Observability must not be weakened by this change: container healthchecks, the compose
        // depends_on conditions, and the edge probes all depend on these being anonymous.
        //
        // The status is not asserted as 200: the aggregate health endpoint authoritatively reports
        // 503 when a component is down, and Redis is intentionally unreachable in this profile.
        // What matters is that the health indicator answered at all — a 401 or 403 would mean the
        // authorization layer had taken over the probe surface.
        int status = mockMvc.perform(get("/actuator/health")).andReturn().getResponse().getStatus();
        assertThat(status).isNotIn(401, 403);
    }

    @Test
    void infoStaysPublic() throws Exception {
        mockMvc.perform(get("/actuator/info"))
                .andExpect(status().isOk());
    }
}
