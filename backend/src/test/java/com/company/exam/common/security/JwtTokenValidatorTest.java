package com.company.exam.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.company.exam.config.JwtProperties;
import com.company.exam.support.TestTokens;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import org.junit.jupiter.api.Test;

class JwtTokenValidatorTest {

    private final JwtTokenValidator validator = new JwtTokenValidator(properties(TestTokens.SECRET));

    @Test
    void acceptsAValidlySignedTokenAndExposesTheSubject() {
        String token = TestTokens.forSubject("11111111-1111-1111-1111-111111111111");

        assertThat(validator.isValid(token)).isTrue();
        assertThat(validator.validate(token).getPayload().getSubject())
                .isEqualTo("11111111-1111-1111-1111-111111111111");
    }

    @Test
    void rejectsATokenFromAnotherIssuer() {
        String token = TestTokens.forSubjectWithIssuer("subject", "another-issuer");

        assertThat(validator.isValid(token)).isFalse();
    }

    @Test
    void rejectsAnExpiredToken() {
        assertThat(validator.isValid(TestTokens.expired("subject"))).isFalse();
    }

    @Test
    void rejectsATokenSignedWithADifferentKey() {
        String foreignSecret = "a-completely-different-secret-value-32bytes";
        String token = Jwts.builder()
                .subject("subject")
                .issuer(TestTokens.ISSUER)
                .issuedAt(new Date())
                .expiration(Date.from(Instant.now().plus(5, ChronoUnit.MINUTES)))
                .signWith(Keys.hmacShaKeyFor(foreignSecret.getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS256)
                .compact();

        assertThat(validator.isValid(token)).isFalse();
    }

    @Test
    void rejectsMalformedInput() {
        assertThat(validator.isValid("not-a-jwt")).isFalse();
        assertThat(validator.isValid("")).isFalse();
    }

    @Test
    void refusesToConstructWithAShortSecret() {
        assertThatThrownBy(() -> new JwtTokenValidator(properties("too-short")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes");
    }

    private JwtProperties properties(String secret) {
        JwtProperties properties = new JwtProperties();
        properties.setIssuer("exam-platform");
        properties.setSecret(secret);
        return properties;
    }
}
