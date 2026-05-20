package com.aquilabuddy.global.auth.jwt;

import java.time.Duration;
import org.springframework.http.ResponseCookie;

/**
 * OAuth 로그인 세션을 담는 JWT cookie 속성을 한 곳에서 고정합니다.
 */
public final class JwtCookieSupport {

    public static final String AUTH_COOKIE_NAME = "aquila_buddy_token";

    private JwtCookieSupport() {
    }

    public static ResponseCookie authenticationCookie(String token, Duration maxAge) {
        return baseCookie(token)
            .maxAge(maxAge)
            .build();
    }

    public static ResponseCookie expiredAuthenticationCookie() {
        return baseCookie("")
            .maxAge(Duration.ZERO)
            .build();
    }

    private static ResponseCookie.ResponseCookieBuilder baseCookie(String value) {
        return ResponseCookie.from(AUTH_COOKIE_NAME, value)
            .httpOnly(true)
            .secure(true)
            .sameSite("Lax")
            .path("/");
    }
}
