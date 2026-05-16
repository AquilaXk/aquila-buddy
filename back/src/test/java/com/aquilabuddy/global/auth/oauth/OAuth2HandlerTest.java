package com.aquilabuddy.global.auth.oauth;

import static org.assertj.core.api.Assertions.assertThat;

import com.aquilabuddy.global.auth.jwt.JwtProperties;
import com.aquilabuddy.global.auth.jwt.JwtProvider;
import com.aquilabuddy.global.auth.principal.OAuthPrincipal;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;

class OAuth2HandlerTest {

    private static final String SECRET = "local-development-jwt-secret-32bytes-minimum-value";

    private final JwtProvider provider = new JwtProvider(new JwtProperties(SECRET, Duration.ofHours(1)));

    @Test
    void successHandlerRedirectsToFrontendCallbackWithToken() throws Exception {
        OAuthPrincipal principal = new OAuthPrincipal("12345", Map.of("login", "aquila-dev"));
        OAuth2SuccessHandler handler = new OAuth2SuccessHandler(provider, "http://localhost:3000/oauth/callback");
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(
            new MockHttpServletRequest(),
            response,
            new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );

        assertThat(response.getRedirectedUrl()).startsWith("http://localhost:3000/oauth/callback?token=");
        String token = response.getRedirectedUrl().substring(response.getRedirectedUrl().indexOf("token=") + 6);
        assertThat(provider.getGithubId(token)).isEqualTo("12345");
    }

    @Test
    void failureHandlerRedirectsToFrontendCallbackWithError() throws Exception {
        OAuth2FailureHandler handler = new OAuth2FailureHandler("http://localhost:3000/oauth/callback");
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationFailure(new MockHttpServletRequest(), response, new AuthenticationException("failed") {
        });

        assertThat(response.getRedirectedUrl()).isEqualTo("http://localhost:3000/oauth/callback?error=oauth_failed");
    }
}
