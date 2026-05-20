package com.aquilabuddy.global.auth.jwt;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class JwtSecretValidatorTest {

    private static final String LOCAL_PLACEHOLDER_SECRET = "local-development-jwt-secret-32bytes-minimum-value";
    private static final String STRONG_SECRET = "production-jwt-secret-value-with-at-least-32-bytes";

    @Test
    void allowsLocalProfileToUseDevelopmentFallbackSecret() {
        JwtSecretValidator validator = new JwtSecretValidator(
            new JwtProperties(LOCAL_PLACEHOLDER_SECRET, Duration.ofHours(1)),
            environment("local")
        );

        assertThatCode(validator::validate).doesNotThrowAnyException();
    }

    @Test
    void rejectsProductionProfileWhenSecretIsDevelopmentFallback() {
        JwtSecretValidator validator = new JwtSecretValidator(
            new JwtProperties(LOCAL_PLACEHOLDER_SECRET, Duration.ofHours(1)),
            environment("prod")
        );

        assertThatThrownBy(validator::validate)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("JWT_SECRET");
    }

    @Test
    void rejectsProductionProfileWhenSecretIsTooShortForHmac() {
        JwtSecretValidator validator = new JwtSecretValidator(
            new JwtProperties("short-secret", Duration.ofHours(1)),
            environment("production")
        );

        assertThatThrownBy(validator::validate)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("at least 32");
    }

    @Test
    void allowsProductionProfileWhenSecretIsStrong() {
        JwtSecretValidator validator = new JwtSecretValidator(
            new JwtProperties(STRONG_SECRET, Duration.ofHours(1)),
            environment("staging")
        );

        assertThatCode(validator::validate).doesNotThrowAnyException();
    }

    private static MockEnvironment environment(String activeProfile) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(activeProfile);
        return environment;
    }
}
