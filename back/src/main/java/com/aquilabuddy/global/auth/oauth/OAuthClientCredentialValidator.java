package com.aquilabuddy.global.auth.oauth;

import jakarta.annotation.PostConstruct;
import java.util.Arrays;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * 운영 profile에서 GitHub OAuth credential placeholder가 token 교환 실패로 이어지지 않도록 기동 시점에 차단합니다.
 */
@Component
public class OAuthClientCredentialValidator {

    private static final Set<String> PROTECTED_PROFILES = Set.of("prod", "production", "staging");
    private static final String LOCAL_CLIENT_ID = "local-client-id";
    private static final String LOCAL_CLIENT_SECRET = "local-client-secret";

    private final String clientId;
    private final String clientSecret;
    private final Environment environment;

    public OAuthClientCredentialValidator(
        @Value("${spring.security.oauth2.client.registration.github.client-id:local-client-id}") String clientId,
        @Value("${spring.security.oauth2.client.registration.github.client-secret:local-client-secret}") String clientSecret,
        Environment environment
    ) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.environment = environment;
    }

    @PostConstruct
    public void validate() {
        if (!hasProtectedProfile()) {
            return;
        }

        if (isMissingOrPlaceholder(clientId, LOCAL_CLIENT_ID)) {
            throw new IllegalStateException(
                "GITHUB_CLIENT_ID must be configured with a non-placeholder value in production."
            );
        }
        if (isMissingOrPlaceholder(clientSecret, LOCAL_CLIENT_SECRET)) {
            throw new IllegalStateException(
                "GITHUB_CLIENT_SECRET must be configured with a non-placeholder value in production."
            );
        }
    }

    private boolean hasProtectedProfile() {
        return Arrays.stream(environment.getActiveProfiles())
            .map(String::toLowerCase)
            .anyMatch(PROTECTED_PROFILES::contains);
    }

    private static boolean isMissingOrPlaceholder(String value, String placeholder) {
        return value == null || value.isBlank() || placeholder.equals(value);
    }
}
