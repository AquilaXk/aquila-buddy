package com.aquilabuddy.global.auth.oauth;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class OAuthClientCredentialValidatorTest {

    @Test
    void allowsLocalProfileToUseDevelopmentFallbackCredentials() {
        OAuthClientCredentialValidator validator = new OAuthClientCredentialValidator(
            "local-client-id",
            "local-client-secret",
            environment("local")
        );

        assertThatCode(validator::validate).doesNotThrowAnyException();
    }

    @Test
    void rejectsProductionProfileWhenClientIdIsDevelopmentFallback() {
        OAuthClientCredentialValidator validator = new OAuthClientCredentialValidator(
            "local-client-id",
            "production-github-client-secret",
            environment("prod")
        );

        assertThatThrownBy(validator::validate)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("GITHUB_CLIENT_ID");
    }

    @Test
    void rejectsStagingProfileWhenClientSecretIsBlank() {
        OAuthClientCredentialValidator validator = new OAuthClientCredentialValidator(
            "production-github-client-id",
            " ",
            environment("staging")
        );

        assertThatThrownBy(validator::validate)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("GITHUB_CLIENT_SECRET");
    }

    @Test
    void allowsProductionProfileWhenCredentialsAreConfigured() {
        OAuthClientCredentialValidator validator = new OAuthClientCredentialValidator(
            "production-github-client-id",
            "production-github-client-secret",
            environment("production")
        );

        assertThatCode(validator::validate).doesNotThrowAnyException();
    }

    private static MockEnvironment environment(String activeProfile) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(activeProfile);
        return environment;
    }
}
