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

    public OAuth2SuccessHandler(
        JwtProvider jwtProvider,
        JwtProperties jwtProperties,
        RefreshTokenSessionService refreshTokenSessionService,
        RefreshTokenProperties refreshTokenProperties,
        @Value("${app.oauth2.redirect-uri:http://localhost:3000/oauth/callback}") String redirectUri
    ) {
        this.jwtProvider = jwtProvider;
        this.jwtProperties = jwtProperties;
        this.refreshTokenSessionService = refreshTokenSessionService;
        this.refreshTokenProperties = refreshTokenProperties;
        this.redirectUri = redirectUri;
    }

    @Override
    public void onAuthenticationSuccess(
        HttpServletRequest request,
        HttpServletResponse response,
        Authentication authentication
    ) throws IOException, ServletException {
        OAuthPrincipal principal = (OAuthPrincipal) authentication.getPrincipal();
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
        response.sendRedirect(redirectUri);
    }

    private static OAuth2AuthenticationException oauthException(String code, String description, Throwable cause) {
        return new OAuth2AuthenticationException(new OAuth2Error(code, description, null), description, cause);
    }
}
