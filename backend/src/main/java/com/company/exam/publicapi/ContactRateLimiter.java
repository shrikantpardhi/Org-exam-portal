package com.company.exam.publicapi;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Fixed-window, in-memory per-key rate limiter for the public contact form (ADR-0037).
 *
 * <p>Single-instance V1.5 uses an in-memory bucket; a multi-instance deployment would move the
 * window to the cache tier. The limits are tunable via {@code app.contact.rate-limit.*}.
 */
@Component
public class ContactRateLimiter {

    private final int maxRequests;
    private final Duration window;
    private final ConcurrentMap<String, Deque<Instant>> buckets = new ConcurrentHashMap<>();

    public ContactRateLimiter(
            @Value("${app.contact.rate-limit.max-requests:5}") int maxRequests,
            @Value("${app.contact.rate-limit.window-seconds:60}") long windowSeconds) {
        this.maxRequests = maxRequests;
        this.window = Duration.ofSeconds(windowSeconds);
    }

    public boolean allow(String key) {
        Instant now = Instant.now();
        Instant cutoff = now.minus(window);
        Deque<Instant> deque = buckets.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        synchronized (deque) {
            while (!deque.isEmpty() && deque.peekFirst().isBefore(cutoff)) {
                deque.pollFirst();
            }
            if (deque.size() >= maxRequests) {
                return false;
            }
            deque.addLast(now);
            return true;
        }
    }

    /** Clears all buckets; exposed so tests are deterministic. */
    public void reset() {
        buckets.clear();
    }
}
