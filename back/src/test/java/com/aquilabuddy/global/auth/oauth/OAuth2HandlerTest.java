package com.aquilabuddy.global.auth.oauth;

import static org.assertj.core.api.Assertions.assertThat;

import com.aquilabuddy.global.auth.jwt.JwtProperties;
import com.aquilabuddy.global.auth.jwt.JwtProvider;
import com.aquilabuddy.global.auth.jwt.JwtCookieSupport;
import com.aquilabuddy.global.auth.principal.OAuthPrincipal;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;

class OAuth2HandlerTest {

    private static final String SECRET = "local-development-jwt-secret-32bytes-minimum-value";

    private final JwtProperties properties = new JwtProperties(SECRET, Duration.ofHours(1));
    private final JwtProvider provider = new JwtProvider(properties);

    @Test
    void successHandlerRedirectsToFrontendCallbackWithSecureCookie() throws Exception {
        OAuthPrincipal principal = new OAuthPrincipal("12345", Map.of("login", "aquila-dev"));
        OAuth2SuccessHandler handler = new OAuth2SuccessHandler(
            provider,
            properties,
            "http://localhost:3000/oauth/callback"
        );
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(
            new MockHttpServletRequest(),
            response,
            new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );

        assertThat(response.getRedirectedUrl()).isEqualTo("http://localhost:3000/oauth/callback");
        String setCookie = response.getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie)
            .contains("__Host-aquila_buddy_token=")
            .contains("HttpOnly")
            .contains("Secure")
            .contains("SameSite=Lax")
            .contains("Path=/")
            .contains("Max-Age=3600")
            .doesNotContain("Domain=");

        String token = setCookie.substring(
            setCookie.indexOf(JwtCookieSupport.AUTH_COOKIE_NAME + "=")
                + JwtCookieSupport.AUTH_COOKIE_NAME.length() + 1,
            setCookie.indexOf(';')
        );
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
