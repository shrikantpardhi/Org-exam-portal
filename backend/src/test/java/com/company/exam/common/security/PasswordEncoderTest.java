package com.company.exam.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

class PasswordEncoderTest {

    private final PasswordEncoder encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();

    @Test
    void hashesWithAnAlgorithmPrefixAndNeverStoresPlaintext() {
        String raw = "S3cret-Passw0rd!";

        String hash = encoder.encode(raw);

        assertThat(hash).isNotEqualTo(raw);
        assertThat(hash).startsWith("{");
    }

    @Test
    void matchesTheOriginalPasswordAndRejectsOthers() {
        String hash = encoder.encode("S3cret-Passw0rd!");

        assertThat(encoder.matches("S3cret-Passw0rd!", hash)).isTrue();
        assertThat(encoder.matches("wrong-password", hash)).isFalse();
    }

    @Test
    void producesDifferentHashesForTheSameInput() {
        String first = encoder.encode("same-input");
        String second = encoder.encode("same-input");

        assertThat(first).isNotEqualTo(second);
    }
}
