package com.company.exam.communication.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.exam.communication.config.CommunicationProperties;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/**
 * The retry backoff policy ({@code specs/19-P10-Communication-V1.5.md §33, §35}).
 */
class RetryPolicyTest {

    private final CommunicationProperties properties = new CommunicationProperties();
    private final RetryPolicy policy = new RetryPolicy(properties);

    @Test
    void delayGrowsExponentiallyFromTheBase() {
        Duration first = policy.delayForAttempt(1);
        Duration second = policy.delayForAttempt(2);
        assertThat(second).isGreaterThan(first);
        assertThat(first).isBetween(Duration.ofSeconds(48), Duration.ofSeconds(72));
    }

    @Test
    void delayIsCapped() {
        for (int attempt : new int[] {1, 2, 3, 4, 5, 6, 7}) {
            assertThat(policy.cappedDelayForAttempt(attempt)).isLessThanOrEqualTo(Duration.ofMinutes(30));
        }
        assertThat(policy.cappedDelayForAttempt(5)).isEqualTo(Duration.ofMinutes(30));
    }

    @Test
    void jitterStaysWithinTwentyPercent() {
        Duration capped = policy.cappedDelayForAttempt(2);
        long min = (long) (capped.toMillis() * 0.80);
        long max = (long) (capped.toMillis() * 1.20);
        for (int i = 0; i < 200; i++) {
            long millis = policy.delayForAttempt(2).toMillis();
            assertThat(millis).isBetween(min - 1, max + 1);
        }
    }

    @Test
    void maximumAttemptsIsBounded() {
        assertThat(policy.maxAttempts()).isEqualTo(5);
    }
}
