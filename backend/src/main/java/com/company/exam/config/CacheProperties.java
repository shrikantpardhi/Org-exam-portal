package com.company.exam.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cache configuration.
 *
 * <p>Redis is cache only ({@code specs/06-Architecture-Freeze-V1.5.md §16}); these values tune
 * the cache-aside behaviour and never define authoritative state.
 */
@ConfigurationProperties(prefix = "app.cache")
public class CacheProperties {

    /** Prefix applied to every cache key to avoid collisions in a shared Redis. */
    private String keyPrefix = "exam:";

    /** Default entry time-to-live when a caller does not specify one. */
    private Duration defaultTtl = Duration.ofMinutes(10);

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public void setKeyPrefix(String keyPrefix) {
        this.keyPrefix = keyPrefix;
    }

    public Duration getDefaultTtl() {
        return defaultTtl;
    }

    public void setDefaultTtl(Duration defaultTtl) {
        this.defaultTtl = defaultTtl;
    }
}
