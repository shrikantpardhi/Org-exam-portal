package com.company.exam.support;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import javax.crypto.SecretKey;

/**
 * Builds JWTs for tests using the same issuer and secret as the {@code test} profile.
 *
 * <p>P0 does not issue tokens; the security smoke tests need a validly signed token to prove that
 * the JWT boundary authenticates a caller and that an authenticated caller without the required
 * authority receives 403.
 */
public final class TestTokens {

    public static final String ISSUER = "exam-platform";
    public static final String SECRET = "test-only-secret-value-at-least-32-characters-long";

    private TestTokens() {
    }

    public static String forSubject(String subject) {
        return Jwts.builder()
                .subject(subject)
                .issuer(ISSUER)
                .issuedAt(new Date())
                .expiration(Date.from(Instant.now().plus(10, ChronoUnit.MINUTES)))
                .signWith(secretKey(), Jwts.SIG.HS256)
                .compact();
    }

    public static String forSubjectWithIssuer(String subject, String issuer) {
        return Jwts.builder()
                .subject(subject)
                .issuer(issuer)
                .issuedAt(new Date())
                .expiration(Date.from(Instant.now().plus(10, ChronoUnit.MINUTES)))
                .signWith(secretKey(), Jwts.SIG.HS256)
                .compact();
    }

    public static String expired(String subject) {
        return Jwts.builder()
                .subject(subject)
                .issuer(ISSUER)
                .issuedAt(Date.from(Instant.now().minus(2, ChronoUnit.HOURS)))
                .expiration(Date.from(Instant.now().minus(1, ChronoUnit.HOURS)))
                .signWith(secretKey(), Jwts.SIG.HS256)
                .compact();
    }

    public static SecretKey secretKey() {
        return Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
    }
}
