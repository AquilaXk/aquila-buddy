package com.aquilabuddy.global.auth;

import com.aquilabuddy.global.auth.csrf.CsrfCookieSupport;
import com.aquilabuddy.global.auth.jwt.JwtCookieSupport;
import com.aquilabuddy.global.auth.jwt.JwtProperties;
import com.aquilabuddy.global.auth.jwt.JwtProvider;
import com.aquilabuddy.global.auth.refresh.InvalidRefreshTokenException;
import com.aquilabuddy.global.auth.refresh.RefreshTokenCookieSupport;
import com.aquilabuddy.global.auth.refresh.RefreshTokenProperties;
import com.aquilabuddy.global.auth.refresh.RefreshTokenSession;
import com.aquilabuddy.global.auth.refresh.RefreshTokenSessionService;
import com.aquilabuddy.global.response.ApiResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * 브라우저 기반 인증 세션의 서버 cookie 상태를 정리합니다.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthSessionController {

    private final JwtProvider jwtProvider;
    private final JwtProperties jwtProperties;
    private final RefreshTokenSessionService refreshTokenSessionService;
    private final RefreshTokenProperties refreshTokenProperties;

    public AuthSessionController(
        JwtProvider jwtProvider,
        JwtProperties jwtProperties,
        RefreshTokenSessionService refreshTokenSessionService,
        RefreshTokenProperties refreshTokenProperties
    ) {
        this.jwtProvider = jwtProvider;
        this.jwtProperties = jwtProperties;
        this.refreshTokenSessionService = refreshTokenSessionService;
        this.refreshTokenProperties = refreshTokenProperties;
    }

    @PostMapping("/refresh")
    ResponseEntity<ApiResponse<Void>> refresh(
        @CookieValue(value = RefreshTokenCookieSupport.REFRESH_COOKIE_NAME, required = false) String refreshToken
    ) {
        try {
            RefreshTokenSession rotatedSession = refreshTokenSessionService.rotate(refreshToken);
            String accessToken = jwtProvider.generateToken(rotatedSession.githubId());

            return ResponseEntity.ok()
                .header(
                    HttpHeaders.SET_COOKIE,
                    JwtCookieSupport.authenticationCookie(accessToken, jwtProperties.expiration()).toString()
                )
                .header(
                    HttpHeaders.SET_COOKIE,
                    CsrfCookieSupport.csrfCookie(CsrfCookieSupport.newToken(), jwtProperties.expiration()).toString()
                )
                .header(
                    HttpHeaders.SET_COOKIE,
                    RefreshTokenCookieSupport.refreshCookie(
                        rotatedSession.refreshToken(),
                        refreshTokenProperties.expiration()
                    ).toString()
                )
                .body(ApiResponse.success(null));
        } catch (InvalidRefreshTokenException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "refresh token이 유효하지 않습니다.");
        }
    }

    @PostMapping("/logout")
    ResponseEntity<ApiResponse<Void>> logout(
        @CookieValue(value = RefreshTokenCookieSupport.REFRESH_COOKIE_NAME, required = false) String refreshToken
    ) {
        refreshTokenSessionService.revoke(refreshToken);

        return ResponseEntity.ok()
            .headers(headers -> {
                JwtCookieSupport.expiredAuthenticationCookies()
                    .forEach(cookie -> headers.add(HttpHeaders.SET_COOKIE, cookie.toString()));
                headers.add(HttpHeaders.SET_COOKIE, CsrfCookieSupport.expiredCsrfCookie().toString());
                RefreshTokenCookieSupport.expiredRefreshCookies()
                    .forEach(cookie -> headers.add(HttpHeaders.SET_COOKIE, cookie.toString()));
            })
            .body(ApiResponse.success(null));
    }
}
