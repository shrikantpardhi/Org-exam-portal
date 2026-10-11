package com.company.exam.common.security;

import com.company.exam.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

/**
 * The JWT decoding and validation boundary.
 *
 * <p>Verifies the signature and issuer and returns the token claims. It does <em>not</em> issue
 * tokens and does not resolve authorities; those belong to P1 Identity
 * ({@code specs/09-P0-Foundation-V1.5.md §31, §32}).
 */
@Component
public class JwtTokenValidator {

    private final SecretKey signingKey;
    private final String issuer;

    public JwtTokenValidator(JwtProperties properties) {
        byte[] secretBytes = properties.getSecret().getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            throw new IllegalStateException("JWT secret must be at least 32 bytes for HS256");
        }
        this.signingKey = Keys.hmacShaKeyFor(secretBytes);
        this.issuer = properties.getIssuer();
    }

    /**
     * Parses and validates a signed token.
     *
     * @throws JwtException if the token is malformed, expired, has a bad signature, or the
     *                      issuer does not match
     */
    public Jws<Claims> validate(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer(issuer)
                .build()
                .parseSignedClaims(token);
    }

    public boolean isValid(String token) {
        try {
            validate(token);
            return true;
        } catch (JwtException | IllegalArgumentException invalidToken) {
            return false;
        }
    }
}
