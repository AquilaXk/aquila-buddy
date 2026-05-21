package com.aquilabuddy.global.auth.oauth;

import static org.assertj.core.api.Assertions.assertThat;

import com.aquilabuddy.global.auth.jwt.JwtProperties;
import com.aquilabuddy.global.auth.jwt.JwtProvider;
import com.aquilabuddy.global.auth.jwt.JwtCookieSupport;
import com.aquilabuddy.global.auth.principal.OAuthPrincipal;
import com.aquilabuddy.global.auth.refresh.RefreshTokenCookieSupport;
import com.aquilabuddy.global.auth.refresh.RefreshTokenProperties;
import com.aquilabuddy.global.auth.refresh.RefreshTokenSession;
import com.aquilabuddy.global.auth.refresh.RefreshTokenSessionService;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;

class OAuth2HandlerTest {

    private static final String SECRET = "local-development-jwt-secret-32bytes-minimum-value";

    private final JwtProperties properties = new JwtProperties(SECRET, Duration.ofHours(1));
    private final JwtProvider provider = new JwtProvider(properties);
    private final RefreshTokenSessionService refreshTokenSessionService =
        org.mockito.Mockito.mock(RefreshTokenSessionService.class);
    private final RefreshTokenProperties refreshTokenProperties = new RefreshTokenProperties(Duration.ofDays(14));

    @Test
    void successHandlerRedirectsToFrontendCallbackWithSecureCookie() throws Exception {
        OAuthPrincipal principal = new OAuthPrincipal("12345", Map.of("login", "aquila-dev"));
        org.mockito.BDDMockito.given(refreshTokenSessionService.issue("12345"))
            .willReturn(new RefreshTokenSession("12345", "refresh-token-value"));
        OAuth2SuccessHandler handler = new OAuth2SuccessHandler(
            provider,
            properties,
            refreshTokenSessionService,
            refreshTokenProperties,
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
        assertThat(response.getHeaders(HttpHeaders.SET_COOKIE))
            .anySatisfy(cookie -> assertThat(cookie)
                .contains(RefreshTokenCookieSupport.REFRESH_COOKIE_NAME + "=refresh-token-value")
                .contains("HttpOnly")
                .contains("Secure")
                .contains("SameSite=Lax")
                .contains("Path=/")
                .doesNotContain("Domain="));
    }

    @Test
    void failureHandlerRedirectsToFrontendCallbackWithError() throws Exception {
        OAuth2FailureHandler handler = new OAuth2FailureHandler("http://localhost:3000/oauth/callback");
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationFailure(new MockHttpServletRequest(), response, new AuthenticationException("failed") {
        });

        assertThat(response.getRedirectedUrl()).isEqualTo("http://localhost:3000/oauth/callback?error=oauth_failed");
    }

    @Test
    void failureHandlerPreservesSafeOAuth2ErrorCode() throws Exception {
        OAuth2FailureHandler handler = new OAuth2FailureHandler("http://localhost:3000/oauth/callback");
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationFailure(
            new MockHttpServletRequest(),
            response,
            new OAuth2AuthenticationException(new OAuth2Error("token_exchange_failed"), "failed")
        );

        assertThat(response.getRedirectedUrl())
            .isEqualTo("http://localhost:3000/oauth/callback?error=token_exchange_failed");
    }

    @Test
    void failureHandlerFallsBackWhenOAuth2ErrorCodeIsUnsafeForQuery() throws Exception {
        OAuth2FailureHandler handler = new OAuth2FailureHandler("http://localhost:3000/oauth/callback");
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationFailure(
            new MockHttpServletRequest(),
            response,
            new OAuth2AuthenticationException(new OAuth2Error("token exchange failed"), "failed")
        );

        assertThat(response.getRedirectedUrl()).isEqualTo("http://localhost:3000/oauth/callback?error=oauth_failed");
    }
}
