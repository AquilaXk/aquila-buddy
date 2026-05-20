package com.aquilabuddy.global.auth;

import com.aquilabuddy.global.auth.csrf.CsrfCookieSupport;
import com.aquilabuddy.global.auth.jwt.JwtCookieSupport;
import com.aquilabuddy.global.response.ApiResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 브라우저 기반 인증 세션의 서버 cookie 상태를 정리합니다.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthSessionController {

    @PostMapping("/logout")
    ResponseEntity<ApiResponse<Void>> logout() {
        return ResponseEntity.ok()
            .headers(headers -> {
                JwtCookieSupport.expiredAuthenticationCookies()
                    .forEach(cookie -> headers.add(HttpHeaders.SET_COOKIE, cookie.toString()));
                headers.add(HttpHeaders.SET_COOKIE, CsrfCookieSupport.expiredCsrfCookie().toString());
            })
            .body(ApiResponse.success(null));
    }
}
