package com.company.exam.communication.service;

import com.company.exam.communication.config.CommunicationProperties;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Component;

/**
 * The bounded retry backoff policy ({@code specs/19-P10-Communication-V1.5.md §33, §34, §35}).
 *
 * <p>Exponential backoff from a 60-second base with a 30-minute cap and plus-or-minus 20 percent
 * jitter so retries do not synchronize into storms. The delay for attempt {@code n} (1-based) is
 * {@code min(cap, base * multiplier^(n-1))} scaled by a random factor in
 * {@code [1 - jitter, 1 + jitter]}.
 */
@Component
public class RetryPolicy {

    private final CommunicationProperties properties;

    public RetryPolicy(CommunicationProperties properties) {
        this.properties = properties;
    }

    /** The number of delivery attempts allowed, including the first. */
    public int maxAttempts() {
        return Math.max(1, properties.getMaxAttempts());
    }

    /** The delay before attempt {@code attemptNumber} (1 = the first retry after the initial send). */
    public Duration delayForAttempt(int attemptNumber) {
        int exponent = Math.max(1, attemptNumber) - 1;
        double raw = properties.getBackoffBase().toMillis() * Math.pow(properties.getBackoffMultiplier(), exponent);
        long capped = (long) Math.min(raw, properties.getBackoffCap().toMillis());
        double jitter = properties.getBackoffJitter();
        double factor = jitter <= 0 ? 1.0 : ThreadLocalRandom.current().nextDouble(1 - jitter, 1 + jitter);
        long millis = Math.max(0, Math.round(capped * factor));
        return Duration.ofMillis(millis);
    }

    /** The maximum delay, used to bound the jittered value in tests. */
    public Duration cappedDelayForAttempt(int attemptNumber) {
        int exponent = Math.max(1, attemptNumber) - 1;
        double raw = properties.getBackoffBase().toMillis() * Math.pow(properties.getBackoffMultiplier(), exponent);
        return Duration.ofMillis((long) Math.min(raw, properties.getBackoffCap().toMillis()));
    }
}
