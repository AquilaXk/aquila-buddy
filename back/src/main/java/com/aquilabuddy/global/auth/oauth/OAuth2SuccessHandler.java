package com.aquilabuddy.global.auth.oauth;

import com.aquilabuddy.global.auth.csrf.CsrfCookieSupport;
import com.aquilabuddy.global.auth.jwt.JwtCookieSupport;
import com.aquilabuddy.global.auth.jwt.JwtProperties;
import com.aquilabuddy.global.auth.jwt.JwtProvider;
import com.aquilabuddy.global.auth.principal.OAuthPrincipal;
import com.aquilabuddy.global.auth.refresh.RefreshTokenCookieSupport;
import com.aquilabuddy.global.auth.refresh.RefreshTokenProperties;
import com.aquilabuddy.global.auth.refresh.RefreshTokenSession;
import com.aquilabuddy.global.auth.refresh.RefreshTokenSessionService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

/**
 * OAuth2 로그인 성공 시 JWT를 HttpOnly cookie로 발급하고 프론트엔드 callback URL로 이동합니다.
 */
@Component
public class OAuth2SuccessHandler implements AuthenticationSuccessHandler {

    private final JwtProvider jwtProvider;
    private final JwtProperties jwtProperties;
    private final RefreshTokenSessionService refreshTokenSessionService;
    private final RefreshTokenProperties refreshTokenProperties;
    private final String redirectUri;
    private final ExtensionOAuthExchangeService extensionOAuthExchangeService;

    public OAuth2SuccessHandler(
        JwtProvider jwtProvider,
        JwtProperties jwtProperties,
        RefreshTokenSessionService refreshTokenSessionService,
        RefreshTokenProperties refreshTokenProperties,
        @Value("${app.oauth2.redirect-uri:http://localhost:3000/oauth/callback}") String redirectUri
    ) {
        this(
            jwtProvider,
            jwtProperties,
            refreshTokenSessionService,
            refreshTokenProperties,
            redirectUri,
            null
        );
    }

    @Autowired
    public OAuth2SuccessHandler(
        JwtProvider jwtProvider,
        JwtProperties jwtProperties,
        RefreshTokenSessionService refreshTokenSessionService,
        RefreshTokenProperties refreshTokenProperties,
        @Value("${app.oauth2.redirect-uri:http://localhost:3000/oauth/callback}") String redirectUri,
        @Autowired(required = false) ExtensionOAuthExchangeService extensionOAuthExchangeService
    ) {
        this.jwtProvider = jwtProvider;
        this.jwtProperties = jwtProperties;
        this.refreshTokenSessionService = refreshTokenSessionService;
        this.refreshTokenProperties = refreshTokenProperties;
        this.redirectUri = redirectUri;
        this.extensionOAuthExchangeService = extensionOAuthExchangeService;
    }

    @Override
    public void onAuthenticationSuccess(
        HttpServletRequest request,
        HttpServletResponse response,
        Authentication authentication
    ) throws IOException, ServletException {
        onAuthenticationSuccess(request, response, authentication, Map.of());
    }

    public void onAuthenticationSuccess(
        HttpServletRequest request,
        HttpServletResponse response,
        Authentication authentication,
        Map<String, Object> flowParameters
    ) throws IOException, ServletException {
        OAuthPrincipal principal = (OAuthPrincipal) authentication.getPrincipal();
        String client = flowParameters == null ? null : stringParam(flowParameters, OAuth2RedirectSupport.PARAM_CLIENT);
        boolean isDesktopClient = OAuth2RedirectSupport.CLIENT_DESKTOP.equals(client);
        String targetRedirectUrl = resolveTargetRedirectUrl(principal.githubId(), flowParameters);

        if (!isDesktopClient) {
            String token;
            RefreshTokenSession refreshTokenSession;
            try {
                token = jwtProvider.generateToken(principal.githubId());
                refreshTokenSession = refreshTokenSessionService.issue(principal.githubId());
            } catch (RuntimeException exception) {
                throw oauthException("refresh_session_failed", "OAuth refresh session 발급에 실패했습니다.", exception);
            }

            response.addHeader(
                HttpHeaders.SET_COOKIE,
                JwtCookieSupport.authenticationCookie(token, jwtProperties.expiration()).toString()
            );
            response.addHeader(
                HttpHeaders.SET_COOKIE,
                CsrfCookieSupport.csrfCookie(CsrfCookieSupport.newToken(), jwtProperties.expiration()).toString()
            );
            response.addHeader(
                HttpHeaders.SET_COOKIE,
                RefreshTokenCookieSupport.refreshCookie(
                    refreshTokenSession.refreshToken(),
                    refreshTokenProperties.expiration()
                ).toString()
            );
        }
        response.sendRedirect(targetRedirectUrl);
    }

    private String resolveTargetRedirectUrl(String githubId, Map<String, Object> flowParameters) {
        if (flowParameters == null || flowParameters.isEmpty()) {
            return redirectUri;
        }
        String client = stringParam(flowParameters, OAuth2RedirectSupport.PARAM_CLIENT);
        if (OAuth2RedirectSupport.CLIENT_EXTENSION.equals(client)) {
            String challenge = stringParam(flowParameters, OAuth2RedirectSupport.PARAM_CODE_CHALLENGE);
            String method = stringParam(flowParameters, OAuth2RedirectSupport.PARAM_CODE_CHALLENGE_METHOD);
            if (extensionOAuthExchangeService == null
                || !OAuth2RedirectSupport.PKCE_METHOD_S256.equals(method)
                || !ExtensionOAuthExchangeService.isValidCodeChallenge(challenge)) {
                throw oauthException("invalid_pkce_request", "유효한 PKCE 요청이 아닙니다.", null);
            }
            try {
                String extCode = extensionOAuthExchangeService.issueCode(githubId, challenge);
                return OAuth2RedirectSupport.appendQueryParam(redirectUri, "ext_code", extCode);
            } catch (RuntimeException exception) {
                throw oauthException("extension_exchange_failed", "Extension 인증 코드 발급에 실패했습니다.", exception);
            }
        }
        if (OAuth2RedirectSupport.CLIENT_DESKTOP.equals(client)) {
            String challenge = stringParam(flowParameters, OAuth2RedirectSupport.PARAM_CODE_CHALLENGE);
            String method = stringParam(flowParameters, OAuth2RedirectSupport.PARAM_CODE_CHALLENGE_METHOD);
            Integer loopbackPort = portParam(flowParameters, OAuth2RedirectSupport.PARAM_LOOPBACK_PORT);
            if (extensionOAuthExchangeService == null
                || !OAuth2RedirectSupport.PKCE_METHOD_S256.equals(method)
                || !ExtensionOAuthExchangeService.isValidCodeChallenge(challenge)
                || loopbackPort == null) {
                throw oauthException("invalid_pkce_request", "유효한 PKCE 요청이 아닙니다.", null);
            }
            try {
                String extCode = extensionOAuthExchangeService.issueCode(githubId, challenge);
                return OAuth2RedirectSupport.appendQueryParam(
                    OAuth2RedirectSupport.buildLoopbackCallbackUri(loopbackPort),
                    "ext_code",
                    extCode
                );
            } catch (RuntimeException exception) {
                throw oauthException("desktop_exchange_failed", "Desktop 인증 코드 발급에 실패했습니다.", exception);
            }
        }

        String safeNext = OAuth2RedirectSupport.sanitizeNext(stringParam(flowParameters, OAuth2RedirectSupport.PARAM_NEXT));
        if (safeNext != null) {
            return OAuth2RedirectSupport.appendQueryParam(redirectUri, "next", safeNext);
        }
        return redirectUri;
    }

    private static Integer portParam(Map<String, Object> flowParameters, String key) {
        Object value = flowParameters.get(key);
        if (value == null) {
            return null;
        }
        return OAuth2RedirectSupport.sanitizeLoopbackPort(String.valueOf(value));
    }

    private static String stringParam(Map<String, Object> flowParameters, String key) {
        Object value = flowParameters.get(key);
        return value instanceof String str ? str : null;
    }

    private static OAuth2AuthenticationException oauthException(String code, String description, Throwable cause) {
        return cause == null
            ? new OAuth2AuthenticationException(new OAuth2Error(code, description, null), description)
            : new OAuth2AuthenticationException(new OAuth2Error(code, description, null), description, cause);
    }
}
