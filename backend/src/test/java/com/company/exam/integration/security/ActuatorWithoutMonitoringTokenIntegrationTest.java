package com.company.exam.integration.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.exam.identity.entity.User;
import com.company.exam.integration.identity.AbstractIdentityIntegrationTest;
import org.junit.jupiter.api.Test;

/**
 * GAP-138: an actuator surface with no monitoring credential configured fails closed.
 *
 * <p>This is the deployment that will actually exist if an operator forgets to set the token, which
 * is the failure mode worth testing. The requirement is that forgetting the credential leaves the
 * metrics endpoint locked, never open — a monitoring token property that defaulted to
 * "no authentication required" would convert one missing environment variable into a leak.
 *
 * <p>Metrics therefore remain reachable through the authenticated route (an operator holding
 * {@code SYSTEM_HEALTH_VIEW}), so production observability is preserved even in this state.
 */
class ActuatorWithoutMonitoringTokenIntegrationTest extends AbstractIdentityIntegrationTest {

    @Test
    void anonymousCallerCannotReadMetricsWhenNoTokenIsConfigured() throws Exception {
        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anEmptyBearerTokenIsNotAccepted() throws Exception {
        // An unconfigured token must not make the empty string a valid credential.
        mockMvc.perform(get("/actuator/prometheus").header(AUTHORIZATION, "Bearer "))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anAdministratorRetainsAccessSoObservabilityIsNotBlinded() throws Exception {
        User administrator = createUser(syntheticEmail("admin-no-token"), "Password123!", "ADMIN");

        mockMvc.perform(get("/actuator/prometheus").header(AUTHORIZATION, bearer(tokenFor(administrator))))
                .andExpect(status().isOk());
    }

    @Test
    void healthRemainsPublicRegardlessOfMonitoringConfiguration() throws Exception {
        // Not asserted as 200: the aggregate indicator answers 503 when a component is down, and
        // Redis is intentionally unreachable in this profile. The point is that the health
        // indicator answered at all instead of the authorization layer.
        int status = mockMvc.perform(get("/actuator/health")).andReturn().getResponse().getStatus();
        assertThat(status).isNotIn(401, 403);
    }
}
