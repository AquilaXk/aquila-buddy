package com.aquilabuddy.global.auth.jwt;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
    String secret,
    Duration expiration
) {
    private static final Duration DEFAULT_EXPIRATION = Duration.ofHours(1);

    public JwtProperties {
        if (expiration == null) {
            expiration = DEFAULT_EXPIRATION;
        }
    }
}
