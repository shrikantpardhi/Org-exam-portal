package com.company.exam.common.cache;

import com.company.exam.config.CacheProperties;
import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/**
 * Redis-backed {@link CacheService} with graceful degradation.
 *
 * <p>If Redis is unavailable every operation degrades to a safe no-op: reads behave as cache
 * misses and the caller falls through to PostgreSQL, while writes and evictions are dropped with
 * a controlled warning. Cache failure never becomes data loss
 * ({@code specs/09-P0-Foundation-V1.5.md §30}, {@code specs/03-Technical-Design-Document-V1.5.md §56}).
 */
@Service
public class RedisCacheService implements CacheService {

    private static final Logger log = LoggerFactory.getLogger(RedisCacheService.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final CacheProperties properties;

    public RedisCacheService(StringRedisTemplate redisTemplate, ObjectMapper objectMapper, CacheProperties properties) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public <T> Optional<T> get(String key, Class<T> type) {
        String cacheKey = cacheKey(key);
        try {
            String cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached == null) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(cached, type));
        } catch (Exception cacheFailure) {
            log.warn("Cache read failed for key={} - falling back to authoritative source: {}",
                    cacheKey, cacheFailure.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void put(String key, Object value, Duration ttl) {
        String cacheKey = cacheKey(key);
        try {
            redisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(value), ttl);
        } catch (Exception cacheFailure) {
            log.warn("Cache write failed for key={}: {}", cacheKey, cacheFailure.getMessage());
        }
    }

    @Override
    public void put(String key, Object value) {
        put(key, value, properties.getDefaultTtl());
    }

    @Override
    public void evict(String key) {
        String cacheKey = cacheKey(key);
        try {
            redisTemplate.delete(cacheKey);
        } catch (Exception cacheFailure) {
            log.warn("Cache eviction failed for key={}: {}", cacheKey, cacheFailure.getMessage());
        }
    }

    private String cacheKey(String key) {
        return properties.getKeyPrefix() + key;
    }
}
