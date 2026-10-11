package com.company.exam.config;

import java.time.Instant;
import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Enables JPA auditing for the standard audit fields.
 *
 * <p>The platform stores instants in UTC ({@code specs/09-P0-Foundation-V1.5.md §100}); the
 * custom {@link DateTimeProvider} supplies an {@link Instant} so that {@code created_on} and
 * {@code updated_on} are timezone-aware rather than a local date-time value.
 */
@Configuration
@EnableJpaAuditing(auditorAwareRef = "currentAuditorProvider", dateTimeProviderRef = "auditingDateTimeProvider")
public class JpaAuditingConfig {

    @Bean
    public DateTimeProvider auditingDateTimeProvider() {
        return () -> Optional.of(Instant.now());
    }
}
