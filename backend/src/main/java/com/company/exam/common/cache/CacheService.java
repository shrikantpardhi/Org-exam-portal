package com.company.exam.common.cache;

import java.time.Duration;
import java.util.Optional;

/**
 * Cache-aside abstraction over the optional cache tier.
 *
 * <p>Redis is an optimization layer, never a source of truth
 * ({@code docs/decisions/ADR-0005-redis-cache-only.md}). Every operation is therefore safe to
 * fail: a cache miss and a cache error are indistinguishable to callers, who must be able to fall
 * back to the authoritative datastore.
 *
 * <p>Callers must never treat a cache value as authoritative for student responses, attempts,
 * payments, results, answer keys, access grants, or audit records.
 */
public interface CacheService {

    /**
     * Reads a cached value.
     *
     * @return the value, or {@link Optional#empty()} on a cache miss <em>or</em> a cache error
     */
    <T> Optional<T> get(String key, Class<T> type);

    /** Writes a value with an explicit time-to-live. Failures are swallowed and logged. */
    void put(String key, Object value, Duration ttl);

    /** Writes a value with the default time-to-live. */
    void put(String key, Object value);

    /** Evicts a value. Failures are swallowed and logged. */
    void evict(String key);
}
