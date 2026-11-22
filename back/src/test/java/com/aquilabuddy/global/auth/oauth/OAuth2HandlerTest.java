package com.aquilabuddy.global.auth.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aquilabuddy.global.auth.jwt.JwtCookieSupport;
import com.aquilabuddy.global.auth.jwt.JwtProperties;
import com.aquilabuddy.global.auth.jwt.JwtProvider;
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
    void successHandlerReportsRefreshSessionFailureWithOAuthErrorCode() {
        OAuthPrincipal principal = new OAuthPrincipal("12345", Map.of("login", "aquila-dev"));
        org.mockito.BDDMockito.given(refreshTokenSessionService.issue("12345"))
            .willThrow(new IllegalStateException("refresh table drift"));
        OAuth2SuccessHandler handler = new OAuth2SuccessHandler(
            provider,
            properties,
            refreshTokenSessionService,
            refreshTokenProperties,
            "http://localhost:3000/oauth/callback"
        );

        OAuth2AuthenticationException exception = assertThrows(
            OAuth2AuthenticationException.class,
            () -> handler.onAuthenticationSuccess(
                new MockHttpServletRequest(),
                new MockHttpServletResponse(),
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
            )
        );

        assertThat(exception.getError().getErrorCode()).isEqualTo("refresh_session_failed");
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

    @Test
    void successHandlerAppendsEncodedSafeNextAndBlocksUnsafeNext() throws Exception {
        OAuthPrincipal principal = new OAuthPrincipal("12345", Map.of("login", "aquila-dev"));
        org.mockito.BDDMockito.given(refreshTokenSessionService.issue("12345"))
            .willReturn(new RefreshTokenSession("12345", "refresh-token-value"));
        OAuth2SuccessHandler handler = new OAuth2SuccessHandler(
            provider,
            properties,
            refreshTokenSessionService,
            refreshTokenProperties,
            "http://localhost:3000/oauth/callback",
            new ExtensionOAuthExchangeService()
        );

        MockHttpServletResponse safeResponse = new MockHttpServletResponse();
        handler.onAuthenticationSuccess(
            new MockHttpServletRequest(),
            safeResponse,
            new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()),
            Map.of(OAuth2RedirectSupport.PARAM_NEXT, "/issues/demo-1")
        );
        assertThat(safeResponse.getRedirectedUrl())
            .isEqualTo("http://localhost:3000/oauth/callback?next=%2Fissues%2Fdemo-1");

        MockHttpServletResponse unsafeResponse = new MockHttpServletResponse();
        handler.onAuthenticationSuccess(
            new MockHttpServletRequest(),
            unsafeResponse,
            new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()),
            Map.of(OAuth2RedirectSupport.PARAM_NEXT, "//evil.com")
        );
        assertThat(unsafeResponse.getRedirectedUrl())
            .isEqualTo("http://localhost:3000/oauth/callback");
    }

    @Test
    void successHandlerIssuesExtensionCodeUsingAmpersandWhenRedirectHasQuery() throws Exception {
        OAuthPrincipal principal = new OAuthPrincipal("12345", Map.of("login", "aquila-dev"));
        org.mockito.BDDMockito.given(refreshTokenSessionService.issue("12345"))
            .willReturn(new RefreshTokenSession("12345", "refresh-token-value"));
        ExtensionOAuthExchangeService exchangeService = new ExtensionOAuthExchangeService();
        OAuth2SuccessHandler handler = new OAuth2SuccessHandler(
            provider,
            properties,
            refreshTokenSessionService,
            refreshTokenProperties,
            "http://localhost:3000/oauth/callback?src=ext",
            exchangeService
        );
        String verifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";
        String challenge = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
            java.security.MessageDigest.getInstance("SHA-256")
                .digest(verifier.getBytes(java.nio.charset.StandardCharsets.US_ASCII))
        );

        MockHttpServletResponse response = new MockHttpServletResponse();
        handler.onAuthenticationSuccess(
            new MockHttpServletRequest(),
            response,
            new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()),
            Map.of(
                OAuth2RedirectSupport.PARAM_CLIENT, OAuth2RedirectSupport.CLIENT_EXTENSION,
                OAuth2RedirectSupport.PARAM_CODE_CHALLENGE, challenge,
                OAuth2RedirectSupport.PARAM_CODE_CHALLENGE_METHOD, OAuth2RedirectSupport.PKCE_METHOD_S256
            )
        );

        String redirectedUrl = response.getRedirectedUrl();
        assertThat(redirectedUrl).startsWith("http://localhost:3000/oauth/callback?src=ext&ext_code=");
        String extCode = redirectedUrl.substring(redirectedUrl.indexOf("ext_code=") + "ext_code=".length());
        assertThat(exchangeService.consumeAndVerify(extCode, verifier)).isEqualTo("12345");
    }

    @Test
    void successHandlerIssuesDesktopCodeToLoopbackWithoutCookiesAndRejectsBadPort() throws Exception {
        OAuthPrincipal principal = new OAuthPrincipal("12345", Map.of("login", "aquila-dev"));
        ExtensionOAuthExchangeService exchangeService = new ExtensionOAuthExchangeService();
        OAuth2SuccessHandler handler = new OAuth2SuccessHandler(
            provider,
            properties,
            refreshTokenSessionService,
            refreshTokenProperties,
            "https://buddy.aquilaxk.site/oauth/callback",
            exchangeService
        );
        String verifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";
        String challenge = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
            java.security.MessageDigest.getInstance("SHA-256")
                .digest(verifier.getBytes(java.nio.charset.StandardCharsets.US_ASCII))
        );

        MockHttpServletResponse response = new MockHttpServletResponse();
        handler.onAuthenticationSuccess(
            new MockHttpServletRequest(),
            response,
            new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()),
            Map.of(
                OAuth2RedirectSupport.PARAM_CLIENT, OAuth2RedirectSupport.CLIENT_DESKTOP,
                OAuth2RedirectSupport.PARAM_CODE_CHALLENGE, challenge,
                OAuth2RedirectSupport.PARAM_CODE_CHALLENGE_METHOD, OAuth2RedirectSupport.PKCE_METHOD_S256,
                OAuth2RedirectSupport.PARAM_LOOPBACK_PORT, "45678"
            )
        );

        assertThat(response.getHeaders(HttpHeaders.SET_COOKIE)).isEmpty();
        assertThat(response.getRedirectedUrl()).startsWith("http://127.0.0.1:45678/oauth/callback?ext_code=");
        String extCode = response.getRedirectedUrl()
            .substring(response.getRedirectedUrl().indexOf("ext_code=") + "ext_code=".length());
        assertThat(exchangeService.consumeAndVerify(extCode, verifier)).isEqualTo("12345");

        assertThrows(
            OAuth2AuthenticationException.class,
            () -> handler.onAuthenticationSuccess(
                new MockHttpServletRequest(),
                new MockHttpServletResponse(),
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()),
                Map.of(
                    OAuth2RedirectSupport.PARAM_CLIENT, OAuth2RedirectSupport.CLIENT_DESKTOP,
                    OAuth2RedirectSupport.PARAM_CODE_CHALLENGE, challenge,
                    OAuth2RedirectSupport.PARAM_CODE_CHALLENGE_METHOD, OAuth2RedirectSupport.PKCE_METHOD_S256,
                    OAuth2RedirectSupport.PARAM_LOOPBACK_PORT, "80"
                )
            )
        );
    }

    @Test
    void failureHandlerRedirectsDesktopFailuresToLoopbackWhenPortIsValid() throws Exception {
        OAuth2FailureHandler handler = new OAuth2FailureHandler("https://buddy.aquilaxk.site/oauth/callback");
        MockHttpServletResponse desktopResponse = new MockHttpServletResponse();

        handler.onAuthenticationFailure(
            new MockHttpServletRequest(),
            desktopResponse,
            new OAuth2AuthenticationException(new OAuth2Error("access_denied"), "denied"),
            Map.of(
                OAuth2RedirectSupport.PARAM_CLIENT, OAuth2RedirectSupport.CLIENT_DESKTOP,
                OAuth2RedirectSupport.PARAM_LOOPBACK_PORT, "45678"
            )
        );
        assertThat(desktopResponse.getRedirectedUrl())
            .isEqualTo("http://127.0.0.1:45678/oauth/callback?error=access_denied");

        MockHttpServletResponse invalidPortResponse = new MockHttpServletResponse();
        handler.onAuthenticationFailure(
            new MockHttpServletRequest(),
            invalidPortResponse,
            new OAuth2AuthenticationException(new OAuth2Error("access_denied"), "denied"),
            Map.of(
                OAuth2RedirectSupport.PARAM_CLIENT, OAuth2RedirectSupport.CLIENT_DESKTOP,
                OAuth2RedirectSupport.PARAM_LOOPBACK_PORT, "80"
            )
        );
        assertThat(invalidPortResponse.getRedirectedUrl())
            .isEqualTo("https://buddy.aquilaxk.site/oauth/callback?error=access_denied");
    }
}
