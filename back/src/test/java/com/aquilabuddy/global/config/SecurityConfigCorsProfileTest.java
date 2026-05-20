package com.aquilabuddy.global.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class SecurityConfigCorsProfileTest {

    private final SecurityConfig securityConfig = new SecurityConfig(null, new ObjectMapper());

    @Test
    void rejectsExtensionWildcardInProductionProfile() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");

        assertThatThrownBy(() -> securityConfig.corsConfigurationSource(
            "https://buddy.aquilaxk.site",
            "chrome-extension://*",
            environment
        ))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("app.cors.extension-allowed-origins");
    }

    @Test
    void allowsExplicitExtensionOriginInStagingProfile() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("staging");

        assertThatCode(() -> securityConfig.corsConfigurationSource(
            "https://buddy.aquilaxk.site",
            "chrome-extension://abcdefghijklmnopabcdefghijklmnop",
            environment
        )).doesNotThrowAnyException();
    }
}
