package com.company.exam.delivery.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Runtime session policy ({@code specs/14-P5-Delivery-V1.5.md §90}).
 *
 * <p>The frozen {@code assessment_session} table has no expiry column, so the expiry of a session is
 * derived from persisted state: the attempt deadline (resolved from the published version and the
 * delivery policy) and the last recorded runtime activity. The idle timeout is a configuration
 * value, not a column, so it can be tuned per deployment without a schema amendment.
 */
@Validated
@ConfigurationProperties(prefix = "app.delivery.session")
public class DeliverySessionProperties {

    /**
     * The idle timeout: a session with no recorded runtime activity for this long is stale.
     *
     * <p>The default is two hours. Runtime activity is recorded on every autosave (the DEFAULT
     * delivery policy autosaves every 10 seconds), so a student who is actually working in the exam
     * refreshes {@code last_activity_on} continuously; only an abandoned connection (a closed laptop
     * or a crashed browser) goes quiet for that long.
     */
    private Duration idleTimeout = Duration.ofHours(2);

    public Duration getIdleTimeout() {
        return idleTimeout;
    }

    public void setIdleTimeout(Duration idleTimeout) {
        this.idleTimeout = idleTimeout;
    }
}
