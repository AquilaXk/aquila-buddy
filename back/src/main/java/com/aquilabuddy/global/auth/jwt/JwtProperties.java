package com.aquilabuddy.global.auth.jwt;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
    String secret,
    Duration expiration
) {
    private static final String DEFAULT_SECRET = "local-development-jwt-secret-32bytes-minimum-value";
    private static final Duration DEFAULT_EXPIRATION = Duration.ofHours(1);

    public JwtProperties {
        if (secret == null || secret.isBlank()) {
            secret = DEFAULT_SECRET;
        }
        if (expiration == null) {
            expiration = DEFAULT_EXPIRATION;
        }
    }
}
