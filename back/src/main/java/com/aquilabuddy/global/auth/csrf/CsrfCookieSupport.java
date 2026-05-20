package com.aquilabuddy.global.auth.csrf;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import org.springframework.http.ResponseCookie;

/**
 * 브라우저에서 읽어 API header로 되돌려 보낼 CSRF cookie 속성과 token 생성을 고정합니다.
 */
public final class CsrfCookieSupport {

    public static final String CSRF_COOKIE_NAME = "aquila_buddy_csrf";
    public static final String CSRF_HEADER_NAME = "X-Aquila-CSRF-Token";

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

    private CsrfCookieSupport() {
    }

    public static String newToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return ENCODER.encodeToString(bytes);
    }

    public static ResponseCookie csrfCookie(String token, Duration maxAge) {
        return baseCookie(token)
            .maxAge(maxAge)
            .build();
    }

    public static ResponseCookie expiredCsrfCookie() {
        return baseCookie("")
            .maxAge(Duration.ZERO)
            .build();
    }

    private static ResponseCookie.ResponseCookieBuilder baseCookie(String value) {
        return ResponseCookie.from(CSRF_COOKIE_NAME, value)
            .httpOnly(false)
            .secure(true)
            .sameSite("Lax")
            .path("/");
    }
}
