package com.aquilabuddy.global.auth.jwt;

import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Set;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * 운영 profile에서 공개된 개발용 JWT secret fallback이 사용되지 않도록 기동 시점에 차단합니다.
 */
@Component
public class JwtSecretValidator {

    private static final Set<String> PROTECTED_PROFILES = Set.of("prod", "production", "staging");
    private static final String LOCAL_PLACEHOLDER_SECRET = "local-development-jwt-secret-32bytes-minimum-value";
    private static final int MIN_SECRET_BYTES = 32;

    private final JwtProperties properties;
    private final Environment environment;

    public JwtSecretValidator(JwtProperties properties, Environment environment) {
        this.properties = properties;
        this.environment = environment;
    }

    @PostConstruct
    public void validate() {
        if (!hasProtectedProfile()) {
            return;
        }

        String secret = properties.secret();
        if (secret == null || secret.isBlank() || LOCAL_PLACEHOLDER_SECRET.equals(secret)) {
            throw new IllegalStateException("JWT_SECRET must be configured with a non-placeholder value in production.");
        }
        if (secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("JWT_SECRET must be at least 32 bytes in production.");
        }
    }

    private boolean hasProtectedProfile() {
        return Arrays.stream(environment.getActiveProfiles())
            .map(String::toLowerCase)
            .anyMatch(PROTECTED_PROFILES::contains);
    }
}
