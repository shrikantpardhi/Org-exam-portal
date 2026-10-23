package com.company.exam.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Monitoring-plane credentials (closes GAP-138, {@code specs/21-P12 §28}).
 *
 * <p>The actuator tree beyond health describes the running system in detail — request rates,
 * error counts, JVM internals, connection-pool saturation, worker backlog. It is required for
 * production observability, so it must not be removed; it must stop being <em>anonymous</em>.
 *
 * <p>The scrape path is a machine-to-machine caller that cannot perform an interactive login, so
 * it authenticates with a dedicated static bearer token scoped to the actuator surface alone.
 * That token is a monitoring credential, never a user credential: it grants authority to read
 * metrics and nothing else, and it is not accepted on {@code /api/**}.
 *
 * <p>No production value is committed. The property is empty by default, which fails closed —
 * an unconfigured deployment leaves the metrics endpoint authenticated-but-unreachable rather
 * than open. {@code docker-compose.yml} supplies a development-only default so the local stack
 * keeps its dashboards.
 */
@ConfigurationProperties(prefix = "app.security.monitoring")
public class MonitoringProperties {

    /**
     * Static bearer token accepted on the actuator surface as the monitoring scope.
     *
     * <p>Blank disables the mechanism entirely; the actuator endpoints then require a normal
     * authenticated principal.
     */
    private String metricsToken = "";

    public String getMetricsToken() {
        return metricsToken;
    }

    public void setMetricsToken(String metricsToken) {
        this.metricsToken = metricsToken == null ? "" : metricsToken;
    }
}
