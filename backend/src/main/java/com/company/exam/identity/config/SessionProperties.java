package com.company.exam.identity.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Session policy ({@code specs/10-P1-Identity-V1.5.md §41}).
 *
 * <p>The frozen {@code login_session} table has no expiry or last-activity column, so session
 * timeout is implemented as an absolute lifetime measured from {@code login_time}. There is no
 * idle timeout because there is nowhere to persist last activity
 * ({@code §41, §42}).
 */
@Validated
@ConfigurationProperties(prefix = "app.security.session")
public class SessionProperties {

    /** Absolute session lifetime; sessions older than this become EXPIRED. */
    private Duration absoluteLifetime = Duration.ofHours(12);

    public Duration getAbsoluteLifetime() {
        return absoluteLifetime;
    }

    public void setAbsoluteLifetime(Duration absoluteLifetime) {
        this.absoluteLifetime = absoluteLifetime;
    }
}
