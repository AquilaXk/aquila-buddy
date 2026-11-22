package com.aquilabuddy.adapter.out.langfuse;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.ai.langfuse")
public record LangfuseProperties(
    boolean enabled,
    String baseUrl,
    String publicKey,
    String secretKey,
    String environment
) {

    private static final String DEFAULT_BASE_URL = "https://cloud.langfuse.com";
    private static final String DEFAULT_ENVIRONMENT = "local";

    public String baseUrlOrDefault() {
        return hasText(baseUrl) ? trimTrailingSlash(baseUrl.trim()) : DEFAULT_BASE_URL;
    }

    public String publicKeyOrThrow() {
        if (!hasText(publicKey)) {
            throw new IllegalStateException("app.ai.langfuse.public-key must not be blank when Langfuse is enabled.");
        }
        return publicKey.trim();
    }

    public String secretKeyOrThrow() {
        if (!hasText(secretKey)) {
            throw new IllegalStateException("app.ai.langfuse.secret-key must not be blank when Langfuse is enabled.");
        }
        return secretKey.trim();
    }

    public String environmentOrDefault() {
        return hasText(environment) ? environment.trim() : DEFAULT_ENVIRONMENT;
    }

    private static String trimTrailingSlash(String value) {
        String result = value;
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isBlank();
    }
}
