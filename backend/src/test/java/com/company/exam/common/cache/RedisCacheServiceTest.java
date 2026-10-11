package com.company.exam.common.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.company.exam.config.CacheProperties;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import tools.jackson.databind.json.JsonMapper;

class RedisCacheServiceTest {

    private final JsonMapper objectMapper = JsonMapper.builder().build();

    @Test
    void returnsCachedValueOnHit() {
        StringRedisTemplate template = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> operations = mock(ValueOperations.class);
        when(template.opsForValue()).thenReturn(operations);
        when(operations.get("exam:sample:1")).thenReturn("{\"name\":\"cached\"}");

        RedisCacheService cache = new RedisCacheService(template, objectMapper, properties());

        Optional<Sample> value = cache.get("sample:1", Sample.class);

        assertThat(value).isPresent();
        assertThat(value.get().name()).isEqualTo("cached");
    }

    @Test
    void treatsCacheMissAsEmpty() {
        StringRedisTemplate template = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> operations = mock(ValueOperations.class);
        when(template.opsForValue()).thenReturn(operations);
        when(operations.get("exam:missing")).thenReturn(null);

        RedisCacheService cache = new RedisCacheService(template, objectMapper, properties());

        assertThat(cache.get("missing", Sample.class)).isEmpty();
    }

    @Test
    void degradesGracefullyWhenRedisIsUnavailable() {
        // A real client pointed at an unused port: every operation fails at the connection.
        RedisStandaloneConfiguration standalone = new RedisStandaloneConfiguration("localhost", 6399);
        LettuceClientConfiguration clientConfiguration = LettuceClientConfiguration.builder()
                .commandTimeout(Duration.ofMillis(500))
                .build();
        LettuceConnectionFactory connectionFactory = new LettuceConnectionFactory(standalone, clientConfiguration);
        connectionFactory.afterPropertiesSet();
        try {
            StringRedisTemplate template = new StringRedisTemplate(connectionFactory);
            template.afterPropertiesSet();
            RedisCacheService cache = new RedisCacheService(template, objectMapper, properties());

            // Reads behave as a miss rather than throwing, so the caller can fall back to PostgreSQL.
            assertThat(cache.get("anything", Sample.class)).isEmpty();
            // Writes and evictions are dropped without propagating the failure.
            cache.put("anything", new Sample("value"));
            cache.put("anything", new Sample("value"), Duration.ofSeconds(30));
            cache.evict("anything");
        } finally {
            connectionFactory.destroy();
        }
    }

    private CacheProperties properties() {
        CacheProperties properties = new CacheProperties();
        properties.setKeyPrefix("exam:");
        return properties;
    }

    record Sample(String name) {
    }
}
