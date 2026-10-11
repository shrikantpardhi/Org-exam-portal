package com.company.exam.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * CORS configuration for trusted frontend origins.
 *
 * <p>No wildcard production origin and no credentialed wildcard are permitted
 * ({@code specs/09-P0-Foundation-V1.5.md §34}).
 */
@ConfigurationProperties(prefix = "app.security.cors")
public class CorsProperties {

    /** Trusted origins allowed to call the API. */
    private List<String> allowedOrigins = List.of("http://localhost:3000");

    public List<String> getAllowedOrigins() {
        return allowedOrigins;
    }

    public void setAllowedOrigins(List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }
}
