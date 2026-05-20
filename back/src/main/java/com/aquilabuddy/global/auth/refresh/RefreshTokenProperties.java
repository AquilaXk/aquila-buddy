package com.aquilabuddy.global.auth.refresh;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.refresh-token")
public record RefreshTokenProperties(
    Duration expiration
) {
    public RefreshTokenProperties {
        if (expiration == null) {
            expiration = Duration.ofDays(14);
        }
    }
}
