package com.aquilabuddy.global.auth.oauth;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * OAuth2 로그인 실패를 프론트엔드 callback 오류 query로 변환합니다.
 */
@Component
public class OAuth2FailureHandler implements AuthenticationFailureHandler {

    private static final String DEFAULT_ERROR_CODE = "oauth_failed";
    private static final String SAFE_ERROR_CODE_PATTERN = "[a-z0-9_]{1,64}";

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
        response.sendRedirect(errorRedirectUri(exception));
    }

    private String errorRedirectUri(AuthenticationException exception) {
        return UriComponentsBuilder.fromUriString(redirectUri)
            .queryParam("error", errorCode(exception))
            .build()
            .encode()
            .toUriString();
    }

    private static String errorCode(AuthenticationException exception) {
        if (exception instanceof OAuth2AuthenticationException oauthException) {
            String code = oauthException.getError().getErrorCode();
            if (code != null && code.matches(SAFE_ERROR_CODE_PATTERN)) {
                return code;
            }
        }
        return DEFAULT_ERROR_CODE;
    }
}
