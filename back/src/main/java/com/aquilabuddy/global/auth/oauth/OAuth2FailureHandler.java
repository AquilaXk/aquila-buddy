package com.aquilabuddy.global.auth.oauth;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

/**
 * OAuth2 로그인 실패를 프론트엔드 callback 오류 query로 변환합니다.
 */
@Component
public class OAuth2FailureHandler implements AuthenticationFailureHandler {

    private final String redirectUri;

    public OAuth2FailureHandler(@Value("${app.oauth2.redirect-uri:http://localhost:3000/oauth/callback}") String redirectUri) {
        this.redirectUri = redirectUri;
    }

    @Override
    public void onAuthenticationFailure(
        HttpServletRequest request,
        HttpServletResponse response,
        AuthenticationException exception
    ) throws IOException, ServletException {
        response.sendRedirect(redirectUri + "?error=oauth_failed");
    }
}
