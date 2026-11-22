package com.aquilabuddy.global.auth.jwt;

import java.time.Duration;
import java.util.List;
import org.springframework.http.ResponseCookie;

/**
 * OAuth 로그인 세션을 담는 JWT cookie 속성을 한 곳에서 고정합니다.
 */
public final class JwtCookieSupport {

    public static final String AUTH_COOKIE_NAME = "__Host-aquila_buddy_token";
    public static final String LEGACY_AUTH_COOKIE_NAME = "aquila_buddy_token";

    private JwtCookieSupport() {
    }

    public static ResponseCookie authenticationCookie(String token, Duration maxAge) {
        return baseCookie(token)
            .maxAge(maxAge)
            .build();
    }

    public static ResponseCookie expiredAuthenticationCookie() {
        return baseCookie(AUTH_COOKIE_NAME, "")
            .maxAge(Duration.ZERO)
            .build();
    }

    public static ResponseCookie expiredLegacyAuthenticationCookie() {
        return baseCookie(LEGACY_AUTH_COOKIE_NAME, "")
            .maxAge(Duration.ZERO)
            .build();
    }

    public static List<ResponseCookie> expiredAuthenticationCookies() {
        return List.of(expiredAuthenticationCookie(), expiredLegacyAuthenticationCookie());
    }

    private static ResponseCookie.ResponseCookieBuilder baseCookie(String value) {
        return baseCookie(AUTH_COOKIE_NAME, value);
    }

    private static ResponseCookie.ResponseCookieBuilder baseCookie(String name, String value) {
        return ResponseCookie.from(name, value)
            .httpOnly(true)
            .secure(true)
            .sameSite("Lax")
            .path("/");
    }
}
