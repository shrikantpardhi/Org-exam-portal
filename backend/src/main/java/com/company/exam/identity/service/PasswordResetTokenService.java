package com.company.exam.identity.service;

import com.company.exam.identity.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

/**
 * Issues and validates password-reset tokens ({@code specs/10-P1-Identity-V1.5.md §24-§27}).
 *
 * <p>The frozen schema has no reset-token table and P1 must not invent one ({@code §24}). The
 * token is therefore a short-lived signed JWT whose single-use guarantee comes from binding it to
 * the account's <em>current</em> password hash: once the password changes, the binding no longer
 * matches and the token can never be replayed ({@code §27, §130}). The token is never logged.
 */
@Component
public class PasswordResetTokenService {

    private static final String PURPOSE_CLAIM = "purpose";
    private static final String PURPOSE_VALUE = "password_reset";
    private static final String HASH_FINGERPRINT_CLAIM = "pwd";

    private final SecretKey signingKey;
    private final String issuer;
    private final Duration resetTtl;

    public PasswordResetTokenService(com.company.exam.config.JwtProperties properties) {
        byte[] secretBytes = properties.getSecret().getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            throw new IllegalStateException("JWT secret must be at least 32 bytes for HS256");
        }
        this.signingKey = Keys.hmacShaKeyFor(secretBytes);
        this.issuer = properties.getIssuer();
        this.resetTtl = Duration.ofMinutes(30);
    }

    /** Issues a reset token bound to the user's current password hash. */
    public String issue(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(user.getId().toString())
                .claim(PURPOSE_CLAIM, PURPOSE_VALUE)
                .claim(HASH_FINGERPRINT_CLAIM, fingerprint(user.getPassword()))
                .issuer(issuer)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(resetTtl)))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    /** Parses a reset token; empty when it is malformed, expired, or not a reset token. */
    public Optional<ResetToken> parse(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .requireIssuer(issuer)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            if (!PURPOSE_VALUE.equals(claims.get(PURPOSE_CLAIM, String.class))) {
                return Optional.empty();
            }
            UUID userId = UUID.fromString(claims.getSubject());
            String fingerprint = claims.get(HASH_FINGERPRINT_CLAIM, String.class);
            if (fingerprint == null) {
                return Optional.empty();
            }
            return Optional.of(new ResetToken(userId, fingerprint));
        } catch (JwtException | IllegalArgumentException invalid) {
            return Optional.empty();
        }
    }

    /** True when the token fingerprint still matches the account's current password hash. */
    public boolean matches(ResetToken token, User user) {
        return fingerprint(user.getPassword()).equals(token.passwordFingerprint());
    }

    /** Opaque parsed reset token: the target user and the password-hash binding. */
    public record ResetToken(UUID userId, String passwordFingerprint) {
    }

    private String fingerprint(String passwordHash) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest((passwordHash == null ? "" : passwordHash).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required", impossible);
        }
    }
}
