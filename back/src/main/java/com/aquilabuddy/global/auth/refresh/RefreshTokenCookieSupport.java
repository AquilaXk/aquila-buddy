package com.aquilabuddy.global.auth.refresh;

import java.time.Duration;
import java.util.List;
import org.springframework.http.ResponseCookie;

/**
 * refresh token은 JavaScript에서 읽을 수 없는 HttpOnly cookie로만 전달합니다.
 */
public final class RefreshTokenCookieSupport {

    public static final String REFRESH_COOKIE_NAME = "__Host-aquila_buddy_refresh";
    public static final String LEGACY_REFRESH_COOKIE_NAME = "aquila_buddy_refresh";

    private RefreshTokenCookieSupport() {
    }

    public static ResponseCookie refreshCookie(String token, Duration maxAge) {
        return baseCookie(REFRESH_COOKIE_NAME, token)
            .maxAge(maxAge)
            .build();
    }

    public static List<ResponseCookie> expiredRefreshCookies() {
        return List.of(
            expiredCookie(REFRESH_COOKIE_NAME),
            expiredCookie(LEGACY_REFRESH_COOKIE_NAME)
        );
    }

    private static ResponseCookie expiredCookie(String name) {
        return baseCookie(name, "")
            .maxAge(Duration.ZERO)
            .build();
    }

    private static ResponseCookie.ResponseCookieBuilder baseCookie(String name, String value) {
        return ResponseCookie.from(name, value)
            .httpOnly(true)
            .secure(true)
            .sameSite("Lax")
            .path("/");
    }
}
