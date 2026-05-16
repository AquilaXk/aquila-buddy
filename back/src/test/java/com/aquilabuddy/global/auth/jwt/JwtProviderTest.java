package com.aquilabuddy.global.auth.jwt;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class JwtProviderTest {

    private static final String SECRET = "local-development-jwt-secret-32bytes-minimum-value";

    @Test
    void generatesValidTokenWithGithubIdSubject() {
        JwtProvider provider = new JwtProvider(new JwtProperties(SECRET, Duration.ofHours(1)));

        String token = provider.generateToken("12345");

        assertThat(provider.isValid(token)).isTrue();
        assertThat(provider.getGithubId(token)).isEqualTo("12345");
    }

    @Test
    void rejectsMalformedToken() {
        JwtProvider provider = new JwtProvider(new JwtProperties(SECRET, Duration.ofHours(1)));

        assertThat(provider.isValid("not-a-jwt")).isFalse();
    }
}
