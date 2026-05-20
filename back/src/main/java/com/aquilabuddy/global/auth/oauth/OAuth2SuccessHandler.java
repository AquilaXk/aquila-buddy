package com.aquilabuddy.global.auth.oauth;

import com.aquilabuddy.global.auth.jwt.JwtCookieSupport;
import com.aquilabuddy.global.auth.jwt.JwtProperties;
import com.aquilabuddy.global.auth.jwt.JwtProvider;
import com.aquilabuddy.global.auth.principal.OAuthPrincipal;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

/**
 * OAuth2 로그인 성공 시 JWT를 HttpOnly cookie로 발급하고 프론트엔드 callback URL로 이동합니다.
 */
@Component
public class OAuth2SuccessHandler implements AuthenticationSuccessHandler {

    private final JwtProvider jwtProvider;
    private final JwtProperties jwtProperties;
    private final String redirectUri;

    public OAuth2SuccessHandler(
        JwtProvider jwtProvider,
        JwtProperties jwtProperties,
        @Value("${app.oauth2.redirect-uri:http://localhost:3000/oauth/callback}") String redirectUri
    ) {
        this.jwtProvider = jwtProvider;
        this.jwtProperties = jwtProperties;
        this.redirectUri = redirectUri;
    }

    @Override
    public void onAuthenticationSuccess(
        HttpServletRequest request,
        HttpServletResponse response,
        Authentication authentication
    ) throws IOException, ServletException {
        OAuthPrincipal principal = (OAuthPrincipal) authentication.getPrincipal();
        String token = jwtProvider.generateToken(principal.githubId());

        response.addHeader(
            HttpHeaders.SET_COOKIE,
            JwtCookieSupport.authenticationCookie(token, jwtProperties.expiration()).toString()
        );
        response.sendRedirect(redirectUri);
    }
}
