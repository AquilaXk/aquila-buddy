package com.aquilabuddy.global.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.ai")
public record AiProperties(
    String provider
) {

    private static final String DEFAULT_PROVIDER = "fixture";

    public String providerOrDefault() {
        return hasText(provider) ? provider.trim() : DEFAULT_PROVIDER;
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isBlank();
    }
}
