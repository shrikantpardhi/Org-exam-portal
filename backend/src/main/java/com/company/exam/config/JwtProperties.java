package com.company.exam.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * JWT verification configuration.
 *
 * <p>P0 establishes the JWT boundary only: it configures decoding and validation. Token
 * issuance, login, and the full claim contract belong to P1 Identity.
 *
 * <p>The secret must be supplied through environment configuration per profile; no production
 * secret is committed ({@code specs/09-P0-Foundation-V1.5.md §107}).
 */
@Validated
@ConfigurationProperties(prefix = "app.security.jwt")
public class JwtProperties {

    /** Expected token issuer; tokens from another issuer are rejected. */
    @NotBlank
    private String issuer = "exam-platform";

    /** HMAC signing secret. Must be at least 32 bytes for HS256. */
    @NotBlank
    @Size(min = 32, message = "JWT secret must be at least 32 characters")
    private String secret;

    /** Access-token lifetime (used by P1 when issuing tokens). */
    private Duration accessTokenTtl = Duration.ofHours(1);

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public Duration getAccessTokenTtl() {
        return accessTokenTtl;
    }

    public void setAccessTokenTtl(Duration accessTokenTtl) {
        this.accessTokenTtl = accessTokenTtl;
    }
}
