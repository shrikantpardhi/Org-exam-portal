package com.company.exam.identity.security;

import com.company.exam.config.JwtProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

/**
 * Issues access tokens ({@code specs/10-P1-Identity-V1.5.md §18, §38}).
 *
 * <p>Claim catalogue is minimal: {@code sub} (user id), {@code sid} (login session id), {@code iss},
 * {@code iat}, {@code exp}. Permissions are never baked into the token; they are resolved per
 * request so that authorization reflects current state ({@code §75, §76}).
 */
@Component
public class JwtTokenIssuer {

    /** Claim carrying the login session id. */
    public static final String SESSION_CLAIM = "sid";

    private final SecretKey signingKey;
    private final String issuer;
    private final Duration accessTokenTtl;

    public JwtTokenIssuer(JwtProperties properties) {
        byte[] secretBytes = properties.getSecret().getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            throw new IllegalStateException("JWT secret must be at least 32 bytes for HS256");
        }
        this.signingKey = Keys.hmacShaKeyFor(secretBytes);
        this.issuer = properties.getIssuer();
        this.accessTokenTtl = properties.getAccessTokenTtl();
    }

    public IssuedToken issue(UUID userId, UUID sessionId) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(accessTokenTtl);
        String token = Jwts.builder()
                .subject(userId.toString())
                .claim(SESSION_CLAIM, sessionId.toString())
                .issuer(issuer)
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
        return new IssuedToken(token, accessTokenTtl.toSeconds());
    }

    public Duration accessTokenTtl() {
        return accessTokenTtl;
    }

    /** A freshly issued access token and its lifetime in seconds. */
    public record IssuedToken(String token, long expiresInSeconds) {
    }
}
